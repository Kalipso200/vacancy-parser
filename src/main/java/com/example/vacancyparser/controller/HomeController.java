package com.example.vacancyparser.controller;

import com.example.vacancyparser.model.ParserTask;
import com.example.vacancyparser.service.parser.VacancyParserService;
import com.example.vacancyparser.service.storage.VacancyJpaStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RestController
@Tag(name = "Home", description = "Базовые эндпоинты для проверки работоспособности")
public class HomeController {

    private final VacancyParserService parserService;
    private final VacancyJpaStorageService storageService;

    public HomeController(VacancyParserService parserService,
                          VacancyJpaStorageService storageService) {
        this.parserService = parserService;
        this.storageService = storageService;
    }

    @GetMapping("/")
    @Operation(summary = "Главная страница", description = "Возвращает информацию о API")
    public Map<String, Object> home() {
        Map<String, Object> response = new HashMap<>();
        response.put("app", "Vacancy Parser API");
        response.put("version", "1.0.0");
        response.put("status", "running");

        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("GET /health", "Health check");
        endpoints.put("GET /api/vacancies", "Get all vacancies (paginated)");
        endpoints.put("GET /api/vacancies/stats", "Get statistics");
        endpoints.put("GET /api/vacancies/tasks", "Get all tasks");
        endpoints.put("GET /api/vacancies/search", "Search vacancies");
        endpoints.put("POST /api/vacancies/parse", "Parse a vacancy");
        endpoints.put("GET /swagger-ui.html", "Swagger UI");
        endpoints.put("GET /actuator/prometheus", "Prometheus metrics");
        endpoints.put("GET /metrics/dashboard", "Metrics dashboard");

        response.put("endpoints", endpoints);
        return response;
    }

    @GetMapping("/health")
    @Operation(summary = "Проверка здоровья")
    public Map<String, String> health() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", String.valueOf(System.currentTimeMillis()));
        return health;
    }

    @GetMapping("/metrics/dashboard")
    @Operation(summary = "Дашборд метрик")
    public Map<String, Object> metricsDashboard() {
        Map<String, Object> dashboard = new HashMap<>();

        try {
            // Статистика парсинга
            int totalParsed = parserService.getTotalParsedCount();
            long failedCount = parserService.getAllTasks().stream()
                    .filter(ParserTask::isFailed)
                    .count();
            int activeCount = parserService.getAllTasks().size();

            dashboard.put("parsing_stats", Map.of(
                    "successful", totalParsed,
                    "failed", failedCount,
                    "active", activeCount
            ));

            // Статистика БД
            dashboard.put("database_stats", Map.of(
                    "total_vacancies", storageService.getTotalCount(),
                    "by_source", storageService.getStatsBySource(),
                    "by_city", storageService.getStatsByCity()
            ));

            // Системная информация
            Runtime runtime = Runtime.getRuntime();
            dashboard.put("system_stats", Map.of(
                    "available_processors", runtime.availableProcessors(),
                    "free_memory_mb", runtime.freeMemory() / (1024 * 1024),
                    "total_memory_mb", runtime.totalMemory() / (1024 * 1024),
                    "max_memory_mb", runtime.maxMemory() / (1024 * 1024),
                    "uptime_seconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000
            ));

        } catch (Exception e) {
            dashboard.put("error", e.getMessage());
        }

        return dashboard;
    }
}