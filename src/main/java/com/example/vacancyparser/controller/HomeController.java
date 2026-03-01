package com.example.vacancyparser.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@Tag(name = "Home", description = "Базовые эндпоинты для проверки работоспособности")
public class HomeController {

    @GetMapping("/")
    @Operation(summary = "Главная страница", description = "Возвращает информацию о API и список всех доступных эндпоинтов")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Успешно"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера", content = @Content)
    })
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
        endpoints.put("POST /api/vacancies/parse-multiple", "Parse multiple vacancies");
        endpoints.put("GET /swagger-ui.html", "Swagger UI documentation");
        endpoints.put("GET /api-docs", "OpenAPI JSON specification");

        response.put("endpoints", endpoints);
        response.put("swagger", "http://localhost:8080/swagger-ui.html");
        return response;
    }

    @GetMapping("/health")
    @Operation(summary = "Проверка здоровья", description = "Эндпоинт для мониторинга работоспособности приложения")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Приложение работает нормально"),
            @ApiResponse(responseCode = "500", description = "Приложение нездорово")
    })
    public Map<String, String> health() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", String.valueOf(System.currentTimeMillis()));
        return health;
    }
}