package com.example.vacancyparser.controller;

import com.example.vacancyparser.model.ParserTask;
import com.example.vacancyparser.model.Vacancy;
import com.example.vacancyparser.service.parser.VacancyParserService;
import com.example.vacancyparser.service.storage.VacancyJpaStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.lenient;

@WebMvcTest(VacancyController.class)
@ActiveProfiles("test")
public class VacancyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private VacancyParserService parserService;

    @MockBean
    private VacancyJpaStorageService storageService;

    private Vacancy testVacancy;
    private ParserTask testTask;

    @BeforeEach
    void setUp() {
        testVacancy = new Vacancy();
        testVacancy.setId(1L);
        testVacancy.setTitle("Java Developer");
        testVacancy.setCompany("Яндекс");
        testVacancy.setCity("Москва");
        testVacancy.setSource("hh.ru");
        testVacancy.setExternalId("ext123");
        testVacancy.setSalary("от 250000 ₽");
        testVacancy.setRequirements("Java, Spring");
        testVacancy.setKeySkills("Java, Spring");
        testVacancy.setPostedDate(LocalDateTime.now());

        testTask = new ParserTask("https://hh.ru/vacancy/123", "hh.ru");
        testTask.setStatus(ParserTask.ParserStatus.COMPLETED);
        testTask.setParsedCount(1);

        // Настройка базовых моков с lenient()
        lenient().when(parserService.getAllTasks()).thenReturn(List.of());
        lenient().when(parserService.getTotalParsedCount()).thenReturn(0);
        lenient().when(storageService.getTotalCount()).thenReturn(0L);
    }

    @Test
    void testGetAllVacancies() throws Exception {
        when(storageService.getVacanciesWithPagination(0, 20, "id", true))
                .thenReturn(List.of(testVacancy));
        when(storageService.getTotalCount()).thenReturn(1L);

        mockMvc.perform(get("/api/vacancies")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Java Developer"));
    }

    @Test
    void testGetVacancyById() throws Exception {
        when(storageService.findById(1L)).thenReturn(testVacancy);

        mockMvc.perform(get("/api/vacancies/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Java Developer"));
    }

    @Test
    void testGetVacancyByIdNotFound() throws Exception {
        when(storageService.findById(999L)).thenReturn(null);

        mockMvc.perform(get("/api/vacancies/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetVacancyByExternalId() throws Exception {
        when(storageService.findByExternalId("ext123")).thenReturn(testVacancy);

        mockMvc.perform(get("/api/vacancies/external/ext123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Java Developer"));
    }

    @Test
    void testGetStats() throws Exception {
        when(storageService.getTotalCount()).thenReturn(10L);
        when(parserService.getAllTasks()).thenReturn(List.of(testTask));
        when(parserService.getTotalParsedCount()).thenReturn(15);
        when(storageService.getStatsBySource()).thenReturn(Map.of("hh.ru", 8L));
        when(storageService.getStatsByCity()).thenReturn(Map.of("Москва", 5L));
        when(storageService.getStatsByDay()).thenReturn(Map.of(LocalDate.now(), 3L));

        mockMvc.perform(get("/api/vacancies/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVacancies").value(10))
                .andExpect(jsonPath("$.activeTasks").value(1))
                .andExpect(jsonPath("$.totalParsed").value(15))
                .andExpect(jsonPath("$.bySource.hh.ru").value(8))
                .andExpect(jsonPath("$.byCity.Москва").value(5));
    }

    @Test
    void testSearchVacanciesByCompany() throws Exception {
        when(storageService.searchByCompany("Яндекс")).thenReturn(List.of(testVacancy));

        mockMvc.perform(get("/api/vacancies/search")
                        .param("company", "Яндекс"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Java Developer"));
    }

    @Test
    void testAdvancedSearch() throws Exception {
        when(storageService.advancedSearch("Яндекс", "Москва", "Java"))
                .thenReturn(List.of(testVacancy));

        mockMvc.perform(get("/api/vacancies/search/advanced")
                        .param("company", "Яндекс")
                        .param("city", "Москва")
                        .param("title", "Java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Java Developer"));
    }

    @Test
    void testSearchBySkill() throws Exception {
        when(storageService.searchByKeySkill("Java")).thenReturn(List.of(testVacancy));

        mockMvc.perform(get("/api/vacancies/search/skill/Java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Java Developer"));
    }

    @Test
    void testParseVacancy() throws Exception {
        when(parserService.parseVacancyAsync(anyString())).thenReturn(testTask);

        Map<String, String> request = Map.of("url", "https://hh.ru/vacancy/123");

        mockMvc.perform(post("/api/vacancies/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void testParseVacancyWithoutUrl() throws Exception {
        Map<String, String> request = Map.of();

        mockMvc.perform(post("/api/vacancies/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("URL is required"));
    }

    @Test
    void testParseMultipleVacancies() throws Exception {
        List<ParserTask> tasks = List.of(testTask);
        when(parserService.parseMultipleVacancies(anyList())).thenReturn(tasks);

        List<String> urls = List.of("https://hh.ru/vacancy/123", "https://hh.ru/vacancy/456");

        mockMvc.perform(post("/api/vacancies/parse-multiple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(urls)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }

    @Test
    void testGetAllTasks() throws Exception {
        when(parserService.getAllTasks()).thenReturn(List.of(testTask));

        mockMvc.perform(get("/api/vacancies/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }

    @Test
    void testGetTaskStatus() throws Exception {
        when(parserService.getTaskStatus("task123")).thenReturn(testTask);

        mockMvc.perform(get("/api/vacancies/tasks/task123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void testGetTaskStatusNotFound() throws Exception {
        when(parserService.getTaskStatus("nonexistent")).thenReturn(null);

        mockMvc.perform(get("/api/vacancies/tasks/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testClearAll() throws Exception {
        mockMvc.perform(delete("/api/vacancies/clear"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("All vacancies cleared"));
    }

    @Test
    void testClearArchived() throws Exception {
        mockMvc.perform(delete("/api/vacancies/clear-archived"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Archived vacancies cleared"));
    }

    @Test
    void testSearchHhVacancies() throws Exception {
        when(parserService.getParserForSource("hh.ru")).thenReturn(null); // Mock will be updated when we have real parser

        mockMvc.perform(get("/api/vacancies/search/hh")
                        .param("text", "Java")
                        .param("area", "1"))
                .andExpect(status().isOk());
    }
}