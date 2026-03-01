package com.example.vacancyparser.controller;

import com.example.vacancyparser.model.ParserTask;
import com.example.vacancyparser.model.Vacancy;
import com.example.vacancyparser.service.parser.VacancyParserService;
import com.example.vacancyparser.service.storage.VacancyJpaStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vacancies")
@Tag(name = "Vacancies", description = "Управление вакансиями и парсинг")
public class VacancyController {
    private final VacancyParserService parserService;
    private final VacancyJpaStorageService storageService;

    public VacancyController(VacancyParserService parserService,
                             VacancyJpaStorageService storageService) {
        this.parserService = parserService;
        this.storageService = storageService;
    }

    @GetMapping
    @Operation(summary = "Получить все вакансии",
            description = "Возвращает список вакансий с поддержкой пагинации и сортировки")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Успешно получен список вакансий",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "400", description = "Неверные параметры запроса",
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера",
                    content = @Content)
    })
    public ResponseEntity<Map<String, Object>> getAllVacancies(
            @Parameter(description = "Номер страницы (начиная с 0)", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Количество элементов на странице", example = "20")
            @RequestParam(defaultValue = "20") int size,

            @Parameter(description = "Поле для сортировки (id, title, company, city, postedDate)",
                    example = "postedDate")
            @RequestParam(defaultValue = "id") String sortBy,

            @Parameter(description = "Направление сортировки (true - возрастание, false - убывание)",
                    example = "false")
            @RequestParam(defaultValue = "true") boolean ascending) {

        List<Vacancy> vacancies = storageService.getVacanciesWithPagination(page, size, sortBy, ascending);

        Map<String, Object> response = new HashMap<>();
        response.put("page", page);
        response.put("size", size);
        response.put("total", storageService.getTotalCount());
        response.put("items", vacancies);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить вакансию по ID",
            description = "Возвращает детальную информацию о конкретной вакансии")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Вакансия найдена",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Vacancy.class))),
            @ApiResponse(responseCode = "404", description = "Вакансия не найдена",
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера",
                    content = @Content)
    })
    public ResponseEntity<Vacancy> getVacancyById(
            @Parameter(description = "ID вакансии в базе данных", required = true, example = "1")
            @PathVariable Long id) {
        Vacancy vacancy = storageService.findById(id);
        return vacancy != null ? ResponseEntity.ok(vacancy) : ResponseEntity.notFound().build();
    }

    @GetMapping("/external/{externalId}")
    @Operation(summary = "Получить вакансию по внешнему ID",
            description = "Возвращает вакансию по её ID из внешнего источника (hh.ru)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Вакансия найдена",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Vacancy.class))),
            @ApiResponse(responseCode = "404", description = "Вакансия не найдена",
                    content = @Content)
    })
    public ResponseEntity<Vacancy> getVacancyByExternalId(
            @Parameter(description = "Внешний ID вакансии (из URL hh.ru)",
                    required = true, example = "130504644")
            @PathVariable String externalId) {
        Vacancy vacancy = storageService.findByExternalId(externalId);
        return vacancy != null ? ResponseEntity.ok(vacancy) : ResponseEntity.notFound().build();
    }

    @GetMapping("/stats")
    @Operation(summary = "Получить статистику",
            description = "Возвращает статистическую информацию о сохраненных вакансиях")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Статистика успешно получена",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalVacancies", storageService.getTotalCount());
        stats.put("activeTasks", parserService.getAllTasks().size());
        stats.put("totalParsed", parserService.getTotalParsedCount());
        stats.put("bySource", storageService.getStatsBySource());
        stats.put("byCity", storageService.getStatsByCity());
        stats.put("byDay", storageService.getStatsByDay());

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/search")
    @Operation(summary = "Поиск вакансий",
            description = "Поиск вакансий по различным критериям")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Поиск выполнен успешно",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = List.class))),
            @ApiResponse(responseCode = "400", description = "Неверные параметры поиска")
    })
    public ResponseEntity<List<Vacancy>> searchVacancies(
            @Parameter(description = "Название компании (частичное совпадение)", example = "Яндекс")
            @RequestParam(required = false) String company,

            @Parameter(description = "Город (частичное совпадение)", example = "Москва")
            @RequestParam(required = false) String city,

            @Parameter(description = "Название вакансии (частичное совпадение)", example = "Java")
            @RequestParam(required = false) String title,

            @Parameter(description = "Источник вакансии", example = "hh.ru")
            @RequestParam(required = false) String source,

            @Parameter(description = "Начальная дата публикации (ISO 8601)",
                    example = "2026-03-01T00:00:00")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,

            @Parameter(description = "Конечная дата публикации (ISO 8601)",
                    example = "2026-03-01T23:59:59")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        List<Vacancy> results;

        if (company != null) results = storageService.searchByCompany(company);
        else if (city != null) results = storageService.searchByCity(city);
        else if (title != null) results = storageService.searchByTitle(title);
        else if (source != null) results = storageService.searchBySource(source);
        else if (from != null && to != null) results = storageService.searchByDateRange(from, to);
        else results = storageService.getAllVacancies();

        return ResponseEntity.ok(results);
    }

    @GetMapping("/search/advanced")
    @Operation(summary = "Расширенный поиск",
            description = "Комбинированный поиск по нескольким параметрам одновременно")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Поиск выполнен успешно"),
            @ApiResponse(responseCode = "400", description = "Неверные параметры поиска")
    })
    public ResponseEntity<List<Vacancy>> advancedSearch(
            @Parameter(description = "Название компании") @RequestParam(required = false) String company,
            @Parameter(description = "Город") @RequestParam(required = false) String city,
            @Parameter(description = "Название вакансии") @RequestParam(required = false) String title) {

        List<Vacancy> results = storageService.advancedSearch(company, city, title);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/search/skill/{skill}")
    @Operation(summary = "Поиск по ключевому навыку",
            description = "Поиск вакансий, содержащих указанный ключевой навык")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Поиск выполнен успешно"),
            @ApiResponse(responseCode = "400", description = "Неверный параметр")
    })
    public ResponseEntity<List<Vacancy>> searchBySkill(
            @Parameter(description = "Ключевой навык для поиска", required = true, example = "Java")
            @PathVariable String skill) {
        List<Vacancy> results = storageService.searchByKeySkill(skill);
        return ResponseEntity.ok(results);
    }

    @PostMapping("/parse")
    @Operation(summary = "Парсинг одной вакансии",
            description = "Создает асинхронную задачу для парсинга вакансии по URL")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Задача принята в обработку",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ParserTask.class))),
            @ApiResponse(responseCode = "400", description = "URL не предоставлен или некорректен",
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Ошибка при создании задачи")
    })
    public ResponseEntity<?> parseVacancy(
            @Parameter(description = "Объект с URL вакансии", required = true,
                    schema = @Schema(example = "{\"url\": \"https://api.hh.ru/vacancies/130800500\"}"))
            @RequestBody Map<String, String> request) {

        String url = request.get("url");
        if (url == null || url.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "URL is required"));
        }

        try {
            ParserTask task = parserService.parseVacancyAsync(url);
            return ResponseEntity.accepted().body(task);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/parse-multiple")
    @Operation(summary = "Парсинг нескольких вакансий",
            description = "Создает задачи для парсинга нескольких вакансий одновременно")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Задачи приняты в обработку"),
            @ApiResponse(responseCode = "400", description = "Список URL пуст или некорректен")
    })
    public ResponseEntity<?> parseMultipleVacancies(
            @Parameter(description = "Список URL вакансий для парсинга", required = true,
                    schema = @Schema(example = "[\"https://hh.ru/vacancy/130504644\", \"https://hh.ru/vacancy/130504645\"]"))
            @RequestBody List<String> urls) {

        if (urls == null || urls.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "URLs list cannot be empty"));
        }

        List<ParserTask> tasks = parserService.parseMultipleVacancies(urls);
        return ResponseEntity.accepted().body(tasks);
    }

    @PostMapping("/parse-reactive")
    @Operation(summary = "Реактивный парсинг",
            description = "Потоковый парсинг с немедленным возвратом результатов по мере готовности")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Поток данных"),
            @ApiResponse(responseCode = "400", description = "Список URL пуст")
    })
    public Flux<Vacancy> parseReactive(
            @Parameter(description = "Список URL вакансий для парсинга", required = true)
            @RequestBody List<String> urls) {
        return parserService.parseMultipleUrlsReactive(urls);
    }

    @GetMapping("/tasks")
    @Operation(summary = "Получить все задачи",
            description = "Возвращает список всех активных задач парсинга")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список задач получен"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка")
    })
    public ResponseEntity<List<ParserTask>> getAllTasks() {
        return ResponseEntity.ok(parserService.getAllTasks());
    }

    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "Получить статус задачи",
            description = "Возвращает детальную информацию о задаче по её ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Задача найдена",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ParserTask.class))),
            @ApiResponse(responseCode = "404", description = "Задача не найдена")
    })
    public ResponseEntity<ParserTask> getTaskStatus(
            @Parameter(description = "ID задачи", required = true,
                    example = "e08de040-e302-4841-9fff-92fe16216185")
            @PathVariable String taskId) {
        ParserTask task = parserService.getTaskStatus(taskId);
        return task != null ? ResponseEntity.ok(task) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/tasks/{taskId}")
    @Operation(summary = "Удалить задачу",
            description = "Удаляет завершенную задачу из списка активных")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Задача успешно удалена"),
            @ApiResponse(responseCode = "404", description = "Задача не найдена")
    })
    public ResponseEntity<Map<String, String>> removeTask(
            @Parameter(description = "ID задачи для удаления", required = true)
            @PathVariable String taskId) {
        parserService.removeCompletedTask(taskId);
        return ResponseEntity.ok(Map.of("message", "Task removed successfully"));
    }

    @DeleteMapping("/clear")
    @Operation(summary = "Очистить все вакансии",
            description = "Удаляет все сохраненные вакансии из базы данных")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Все вакансии удалены"),
            @ApiResponse(responseCode = "500", description = "Ошибка при удалении")
    })
    public ResponseEntity<Map<String, String>> clearAll() {
        storageService.clearAll();
        return ResponseEntity.ok(Map.of("message", "All vacancies cleared"));
    }

    @DeleteMapping("/clear-archived")
    @Operation(summary = "Очистить архивные вакансии",
            description = "Удаляет все архивные вакансии из базы данных")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Архивные вакансии удалены"),
            @ApiResponse(responseCode = "500", description = "Ошибка при удалении")
    })
    public ResponseEntity<Map<String, String>> clearArchived() {
        storageService.deleteAllArchived();
        return ResponseEntity.ok(Map.of("message", "Archived vacancies cleared"));
    }
}