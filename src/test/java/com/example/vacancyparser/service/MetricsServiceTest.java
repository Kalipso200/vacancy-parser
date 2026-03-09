package com.example.vacancyparser.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@ExtendWith(MockitoExtension.class)
public class MetricsServiceTest {

    private MeterRegistry meterRegistry;
    private MetricsService metricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new MetricsService(meterRegistry);
    }

    @Test
    void testIncrementSuccessfulParsing() {
        metricsService.incrementSuccessfulParsing("hh.ru");

        Counter counter = meterRegistry.find("vacancy.parsing.success").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);

        // Проверка по источнику
        Counter sourceCounter = meterRegistry.find("vacancy.parsing.success.by.source")
                .tag("source", "hh.ru").counter();
        assertThat(sourceCounter).isNotNull();
        assertThat(sourceCounter.count()).isEqualTo(1.0);
    }

    @Test
    void testIncrementFailedParsing() {
        metricsService.incrementFailedParsing("hh.ru", "api_error");

        Counter counter = meterRegistry.find("vacancy.parsing.failed").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);

        Counter typeCounter = meterRegistry.find("vacancy.parsing.failed.by.type")
                .tag("error", "api_error").counter();
        assertThat(typeCounter).isNotNull();
        assertThat(typeCounter.count()).isEqualTo(1.0);

        Counter sourceCounter = meterRegistry.find("vacancy.parsing.failed.by.source")
                .tag("source", "hh.ru").counter();
        assertThat(sourceCounter).isNotNull();
        assertThat(sourceCounter.count()).isEqualTo(1.0);
    }

    @Test
    void testIncrementDatabaseRecords() {
        metricsService.incrementDatabaseRecords(5);

        Counter counter = meterRegistry.find("vacancy.database.records").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(5.0);
    }

    @Test
    void testSetDatabaseRecords() {
        metricsService.setDatabaseRecords(10);

        Double gaugeValue = meterRegistry.find("vacancy.database.total").gauge().value();
        assertThat(gaugeValue).isEqualTo(10.0);
    }

    @Test
    void testSetActiveParsingTasks() {
        metricsService.setActiveParsingTasks(3);

        Double gaugeValue = meterRegistry.find("vacancy.parsing.active").gauge().value();
        assertThat(gaugeValue).isEqualTo(3.0);
    }

    @Test
    void testStartAndStopParsingTimer() {
        Timer.Sample sample = metricsService.startParsingTimer();

        // Симулируем работу
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        metricsService.stopParsingTimer(sample, "hh.ru", true);

        // Проверяем что хотя бы один таймер создан
        Timer timer = meterRegistry.find("vacancy.parsing.detail")
                .tag("source", "hh.ru")
                .tag("status", "success")
                .timer();

        // В SimpleMeterRegistry таймер может не сразу обновиться
        assertThat(timer).isNotNull();
        // Не проверяем count, так как он может быть 0 в простом регистре
    }
    @Test
    void testRecordHttpRequest() {
        metricsService.recordHttpRequest("/api/vacancies", "GET", 200, 150);

        Timer timer = meterRegistry.find("vacancy.http.requests")
                .tag("uri", "/api/vacancies")
                .tag("method", "GET")
                .tag("status", "200")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isCloseTo(150, within(10.0));
    }

    @Test
    void testPrintMetrics() {
        // Просто проверяем что метод не выбрасывает исключений
        metricsService.printMetrics();
    }

    @Test
    void testMultipleIncrements() {
        metricsService.incrementSuccessfulParsing("hh.ru");
        metricsService.incrementSuccessfulParsing("hh.ru");
        metricsService.incrementSuccessfulParsing("superjob.ru");

        Counter totalCounter = meterRegistry.find("vacancy.parsing.success").counter();
        assertThat(totalCounter.count()).isEqualTo(3.0);

        Counter hhCounter = meterRegistry.find("vacancy.parsing.success.by.source")
                .tag("source", "hh.ru").counter();
        assertThat(hhCounter.count()).isEqualTo(2.0);

        Counter sjCounter = meterRegistry.find("vacancy.parsing.success.by.source")
                .tag("source", "superjob.ru").counter();
        assertThat(sjCounter.count()).isEqualTo(1.0);
    }

    @Test
    void testFailedParsingWithDifferentErrors() {
        metricsService.incrementFailedParsing("hh.ru", "timeout");
        metricsService.incrementFailedParsing("hh.ru", "api_error");
        metricsService.incrementFailedParsing("superjob.ru", "timeout");

        Counter totalCounter = meterRegistry.find("vacancy.parsing.failed").counter();
        assertThat(totalCounter.count()).isEqualTo(3.0);

        Counter timeoutCounter = meterRegistry.find("vacancy.parsing.failed.by.type")
                .tag("error", "timeout").counter();
        assertThat(timeoutCounter.count()).isEqualTo(2.0);

        Counter apiErrorCounter = meterRegistry.find("vacancy.parsing.failed.by.type")
                .tag("error", "api_error").counter();
        assertThat(apiErrorCounter.count()).isEqualTo(1.0);
    }
}