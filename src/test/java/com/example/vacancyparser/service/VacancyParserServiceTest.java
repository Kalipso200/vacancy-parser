package com.example.vacancyparser.service.parser;

import com.example.vacancyparser.model.ParserTask;
import com.example.vacancyparser.model.Vacancy;
import com.example.vacancyparser.service.MetricsService;
import com.example.vacancyparser.service.RequestLogDaemon;
import com.example.vacancyparser.service.storage.VacancyJpaStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class VacancyParserServiceTest {

    @Mock
    private VacancyJpaStorageService storageService;

    @Mock
    private RequestLogDaemon requestLogDaemon;

    @Mock
    private MetricsService metricsService;

    @Mock
    private SiteParser hhRuParser;

    private Executor parserExecutor;
    private ForkJoinPool forkJoinPool;
    private List<SiteParser> parsers;

    private VacancyParserService parserService;

    @BeforeEach
    void setUp() {
        parserExecutor = Executors.newFixedThreadPool(2);
        forkJoinPool = ForkJoinPool.commonPool();
        parsers = List.of(hhRuParser);

        parserService = new VacancyParserService(
                parserExecutor,
                forkJoinPool,
                parsers,
                storageService,
                requestLogDaemon,
                metricsService
        );

        lenient().when(hhRuParser.supports(anyString())).thenReturn(true);
        lenient().when(hhRuParser.getSourceName()).thenReturn("hh.ru");
    }

    @Test
    void testParseVacancyAsync_Success() throws InterruptedException {
        String url = "https://hh.ru/vacancy/123";
        Vacancy expectedVacancy = new Vacancy();
        expectedVacancy.setTitle("Java Developer");
        expectedVacancy.setCompany("Яндекс");

        when(hhRuParser.parse(url)).thenReturn(expectedVacancy);
        when(storageService.save(any(Vacancy.class))).thenReturn(expectedVacancy);

        ParserTask task = parserService.parseVacancyAsync(url);

        assertThat(task).isNotNull();
        assertThat(task.getUrl()).isEqualTo(url);
        assertThat(task.getSource()).isEqualTo("hh.ru");
        assertThat(task.getStatus()).isEqualTo(ParserTask.ParserStatus.PENDING);

        // Ждем завершения задачи
        Thread.sleep(1000);

        ParserTask updatedTask = parserService.getTaskStatus(task.getTaskId());
        assertThat(updatedTask.getStatus()).isEqualTo(ParserTask.ParserStatus.COMPLETED);
        assertThat(updatedTask.getParsedCount()).isEqualTo(1);

        verify(storageService, times(1)).save(any(Vacancy.class));
        verify(metricsService, times(1)).incrementSuccessfulParsing("hh.ru");
        verify(metricsService, times(1)).incrementDatabaseRecords(1);
    }

    @Test
    void testParseVacancyAsync_NoParserFound() {
        String url = "https://unknown-site.com/vacancy/123";
        when(hhRuParser.supports(url)).thenReturn(false);

        assertThatThrownBy(() -> parserService.parseVacancyAsync(url))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No parser found");
    }

    @Test
    void testParseVacancyAsync_ParserReturnsNull() throws InterruptedException {
        String url = "https://hh.ru/vacancy/123";
        when(hhRuParser.parse(url)).thenReturn(null);

        ParserTask task = parserService.parseVacancyAsync(url);

        assertThat(task).isNotNull();

        Thread.sleep(1000);

        ParserTask updatedTask = parserService.getTaskStatus(task.getTaskId());
        assertThat(updatedTask.getStatus()).isEqualTo(ParserTask.ParserStatus.FAILED);
        assertThat(updatedTask.getErrorMessage()).isEqualTo("Failed to parse vacancy");

        verify(storageService, never()).save(any(Vacancy.class));
        verify(metricsService, times(1)).incrementFailedParsing(eq("hh.ru"), eq("null_response"));
    }

    @Test
    void testParseVacancyAsync_Exception() throws InterruptedException {
        String url = "https://hh.ru/vacancy/123";
        when(hhRuParser.parse(url)).thenThrow(new RuntimeException("API error"));

        ParserTask task = parserService.parseVacancyAsync(url);

        assertThat(task).isNotNull();

        Thread.sleep(1000);

        ParserTask updatedTask = parserService.getTaskStatus(task.getTaskId());
        assertThat(updatedTask.getStatus()).isEqualTo(ParserTask.ParserStatus.FAILED);
        assertThat(updatedTask.getErrorMessage()).isEqualTo("API error");

        verify(storageService, never()).save(any(Vacancy.class));
        verify(metricsService, times(1)).incrementFailedParsing(eq("hh.ru"), eq("RuntimeException"));
    }

    @Test
    void testParseMultipleVacancies() {
        List<String> urls = List.of(
                "https://hh.ru/vacancy/1",
                "https://hh.ru/vacancy/2"
        );

        Vacancy vacancy1 = new Vacancy();
        vacancy1.setTitle("Java Developer");

        Vacancy vacancy2 = new Vacancy();
        vacancy2.setTitle("Python Developer");

        when(hhRuParser.parse(anyString()))
                .thenReturn(vacancy1)
                .thenReturn(vacancy2);
        when(storageService.save(any(Vacancy.class))).thenReturn(vacancy1, vacancy2);

        List<ParserTask> tasks = parserService.parseMultipleVacancies(urls);

        assertThat(tasks).hasSize(2);
        assertThat(tasks).extracting(ParserTask::getUrl)
                .containsExactly("https://hh.ru/vacancy/1", "https://hh.ru/vacancy/2");
    }

    @Test
    void testParseMultipleUrlsReactive() {
        List<String> urls = List.of(
                "https://hh.ru/vacancy/1",
                "https://hh.ru/vacancy/2"
        );

        Vacancy vacancy1 = new Vacancy();
        vacancy1.setTitle("Java Developer");
        vacancy1.setCompany("Яндекс");

        Vacancy vacancy2 = new Vacancy();
        vacancy2.setTitle("Python Developer");
        vacancy2.setCompany("Тинькофф");

        when(hhRuParser.parse(anyString()))
                .thenReturn(vacancy1)
                .thenReturn(vacancy2);
        when(storageService.save(any(Vacancy.class))).thenReturn(vacancy1, vacancy2);

        StepVerifier.create(parserService.parseMultipleUrlsReactive(urls))
                .expectNext(vacancy1)
                .expectNext(vacancy2)
                .verifyComplete();

        verify(storageService, times(2)).save(any(Vacancy.class));
    }

    @Test
    void testGetTaskStatus() throws InterruptedException {
        String url = "https://hh.ru/vacancy/123";
        Vacancy vacancy = new Vacancy();
        vacancy.setTitle("Java Developer");

        when(hhRuParser.parse(url)).thenReturn(vacancy);
        when(storageService.save(any(Vacancy.class))).thenReturn(vacancy);

        ParserTask task = parserService.parseVacancyAsync(url);

        Thread.sleep(500);

        ParserTask foundTask = parserService.getTaskStatus(task.getTaskId());
        assertThat(foundTask).isNotNull();
        assertThat(foundTask.getTaskId()).isEqualTo(task.getTaskId());
    }

    @Test
    void testGetAllTasks() throws InterruptedException {
        String url1 = "https://hh.ru/vacancy/1";
        String url2 = "https://hh.ru/vacancy/2";

        Vacancy vacancy = new Vacancy();
        vacancy.setTitle("Java Developer");

        when(hhRuParser.parse(anyString())).thenReturn(vacancy);
        when(storageService.save(any(Vacancy.class))).thenReturn(vacancy);

        parserService.parseVacancyAsync(url1);
        parserService.parseVacancyAsync(url2);

        Thread.sleep(500);

        List<ParserTask> tasks = parserService.getAllTasks();

        assertThat(tasks).hasSize(2);
    }

    @Test
    void testRemoveCompletedTask() throws InterruptedException {
        String url = "https://hh.ru/vacancy/123";
        Vacancy vacancy = new Vacancy();
        vacancy.setTitle("Java Developer");

        when(hhRuParser.parse(url)).thenReturn(vacancy);
        when(storageService.save(any(Vacancy.class))).thenReturn(vacancy);

        ParserTask task = parserService.parseVacancyAsync(url);

        Thread.sleep(1000);

        assertThat(parserService.getAllTasks()).hasSize(1);

        parserService.removeCompletedTask(task.getTaskId());

        assertThat(parserService.getAllTasks()).isEmpty();
    }

    @Test
    void testGetTotalParsedCount() throws InterruptedException {
        String url1 = "https://hh.ru/vacancy/1";
        String url2 = "https://hh.ru/vacancy/2";

        Vacancy vacancy = new Vacancy();
        vacancy.setTitle("Java Developer");

        when(hhRuParser.parse(anyString())).thenReturn(vacancy);
        when(storageService.save(any(Vacancy.class))).thenReturn(vacancy);

        assertThat(parserService.getTotalParsedCount()).isEqualTo(0);

        parserService.parseVacancyAsync(url1);
        parserService.parseVacancyAsync(url2);

        Thread.sleep(1000);

        assertThat(parserService.getTotalParsedCount()).isEqualTo(2);
    }

    @Test
    void testClearCompletedTasks() throws InterruptedException {
        String url1 = "https://hh.ru/vacancy/1";
        String url2 = "https://hh.ru/vacancy/2";

        Vacancy vacancy = new Vacancy();
        vacancy.setTitle("Java Developer");

        when(hhRuParser.parse(anyString())).thenReturn(vacancy);
        when(storageService.save(any(Vacancy.class))).thenReturn(vacancy);

        parserService.parseVacancyAsync(url1);
        parserService.parseVacancyAsync(url2);

        Thread.sleep(1000);

        assertThat(parserService.getAllTasks()).hasSize(2);

        parserService.clearCompletedTasks();

        assertThat(parserService.getAllTasks()).isEmpty();
    }

    @Test
    void testGetParserForSource() {
        when(hhRuParser.getSourceName()).thenReturn("hh.ru");

        SiteParser found = parserService.getParserForSource("hh.ru");
        assertThat(found).isNotNull();
        assertThat(found.getSourceName()).isEqualTo("hh.ru");

        SiteParser notFound = parserService.getParserForSource("nonexistent");
        assertThat(notFound).isNull();
    }
}