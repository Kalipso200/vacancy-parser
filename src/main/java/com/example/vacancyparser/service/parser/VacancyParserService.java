package com.example.vacancyparser.service.parser;

import com.example.vacancyparser.model.ParserTask;
import com.example.vacancyparser.model.Vacancy;
import com.example.vacancyparser.service.MetricsService;
import com.example.vacancyparser.service.RequestLogDaemon;
import com.example.vacancyparser.service.storage.VacancyJpaStorageService;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class VacancyParserService {
    private final Executor parserExecutor;
    private final ForkJoinPool forkJoinPool;
    private final List<SiteParser> parsers;
    private final VacancyJpaStorageService storageService;
    private final RequestLogDaemon requestLogDaemon;
    private final MetricsService metricsService;
    private final ConcurrentHashMap<String, ParserTask> activeTasks = new ConcurrentHashMap<>();
    private final AtomicInteger totalParsedCount = new AtomicInteger(0);

    public VacancyParserService(
            @Qualifier("parserExecutor") Executor parserExecutor,
            ForkJoinPool forkJoinPool,
            List<SiteParser> parsers,
            VacancyJpaStorageService storageService,
            RequestLogDaemon requestLogDaemon,
            MetricsService metricsService) {
        this.parserExecutor = parserExecutor;
        this.forkJoinPool = forkJoinPool;
        this.parsers = parsers;
        this.storageService = storageService;
        this.requestLogDaemon = requestLogDaemon;
        this.metricsService = metricsService;
    }

    /**
     * Синхронизация счетчика с базой данных при старте приложения
     */
    @PostConstruct
    public void initCounter() {
        long dbCount = storageService.getTotalCount();
        totalParsedCount.set((int) dbCount);
        metricsService.setDatabaseRecords(dbCount);

        System.out.println("==========================================");
        System.out.println(" VacancyParserService инициализирован:");
        System.out.println("   - Записей в БД: " + dbCount);
        System.out.println("   - Счетчик парсинга: " + totalParsedCount.get());
        System.out.println("   - Доступно парсеров: " + parsers.size());
        System.out.println("==========================================");

        requestLogDaemon.addLog("INFO", "Parser",
                String.format("Сервис инициализирован. БД: %d записей", dbCount));
    }

    public ParserTask parseVacancyAsync(String url) {
        SiteParser parser = findParserForUrl(url);
        if (parser == null) {
            String error = "No parser found for URL: " + url;
            metricsService.incrementFailedParsing("unknown", "no_parser");
            requestLogDaemon.addLog("ERROR", "Parser", error);
            throw new IllegalArgumentException(error);
        }

        ParserTask task = new ParserTask(url, parser.getSourceName());
        activeTasks.put(task.getTaskId(), task);
        metricsService.setActiveParsingTasks(activeTasks.size());

        // Начинаем измерение времени
        Timer.Sample timerSample = metricsService.startParsingTimer();
        long startTime = System.currentTimeMillis();

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            task.setStatus(ParserTask.ParserStatus.RUNNING);

            try {
                Vacancy vacancy = parser.parse(url);
                long duration = System.currentTimeMillis() - startTime;

                if (vacancy != null) {
                    // Сохраняем в БД
                    storageService.save(vacancy);
                    task.setParsedCount(1);

                    // Увеличиваем счетчик успешных парсингов
                    totalParsedCount.incrementAndGet();

                    // Обновляем метрики
                    metricsService.incrementSuccessfulParsing(parser.getSourceName());
                    metricsService.incrementDatabaseRecords(1);
                    metricsService.stopParsingTimer(timerSample, parser.getSourceName(), true);

                    // Логируем успех
                    requestLogDaemon.addLog("INFO", "Parser",
                            String.format(" Успешно спарсена вакансия '%s' за %d мс",
                                    vacancy.getTitle(), duration));

                    // Параллельная обработка
                    forkJoinPool.submit(() -> processVacancyInParallel(vacancy)).join();

                    task.setStatus(ParserTask.ParserStatus.COMPLETED);

                } else {
                    task.setStatus(ParserTask.ParserStatus.FAILED);
                    task.setErrorMessage("Failed to parse vacancy");

                    // Обновляем метрики ошибок
                    metricsService.incrementFailedParsing(parser.getSourceName(), "null_response");
                    metricsService.stopParsingTimer(timerSample, parser.getSourceName(), false);

                    requestLogDaemon.addLog("ERROR", "Parser",
                            String.format(" Не удалось спарсить вакансию (время: %d мс)", duration));
                }
            } catch (Exception e) {
                task.setStatus(ParserTask.ParserStatus.FAILED);
                task.setErrorMessage(e.getMessage());

                // Обновляем метрики ошибок
                metricsService.incrementFailedParsing(parser.getSourceName(), e.getClass().getSimpleName());
                metricsService.stopParsingTimer(timerSample, parser.getSourceName(), false);

                requestLogDaemon.addLog("ERROR", "Parser",
                        String.format(" Исключение: %s", e.getMessage()));
                e.printStackTrace();
            } finally {
                metricsService.setActiveParsingTasks(activeTasks.size());
                metricsService.setDatabaseRecords(storageService.getTotalCount());

                // Периодически выводим метрики (каждые 10 успешных парсингов)
                if (totalParsedCount.get() % 10 == 0 && totalParsedCount.get() > 0) {
                    metricsService.printMetrics();
                }
            }
        }, parserExecutor);

        task.setFuture(future);
        return task;
    }

    public List<ParserTask> parseMultipleVacancies(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            requestLogDaemon.addLog("WARN", "Parser", "Попытка парсинга пустого списка URL");
            return List.of();
        }

        requestLogDaemon.addLog("INFO", "Parser",
                String.format("Запуск множественного парсинга: %d URL", urls.size()));

        metricsService.setActiveParsingTasks(activeTasks.size() + urls.size());

        List<ParserTask> tasks = urls.stream()
                .map(this::parseVacancyAsync)
                .toList();

        return tasks;
    }

    public Flux<Vacancy> parseMultipleUrlsReactive(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return Flux.empty();
        }

        AtomicInteger counter = new AtomicInteger(0);
        int total = urls.size();

        requestLogDaemon.addLog("INFO", "Reactive",
                String.format("🚀 Запуск реактивного парсинга: %d URL", total));

        metricsService.setActiveParsingTasks(activeTasks.size() + urls.size());

        return Flux.fromIterable(urls)
                .parallel()
                .runOn(Schedulers.fromExecutor(parserExecutor))
                .flatMap(url -> {
                    Timer.Sample timerSample = metricsService.startParsingTimer();
                    long startTime = System.currentTimeMillis();

                    try {
                        SiteParser parser = findParserForUrl(url);
                        if (parser != null) {
                            Vacancy vacancy = parser.parse(url);
                            long duration = System.currentTimeMillis() - startTime;

                            if (vacancy != null) {
                                storageService.save(vacancy);
                                int current = counter.incrementAndGet();

                                // Увеличиваем счетчик успешных парсингов
                                totalParsedCount.incrementAndGet();

                                // Обновляем метрики
                                metricsService.incrementSuccessfulParsing(parser.getSourceName());
                                metricsService.incrementDatabaseRecords(1);
                                metricsService.stopParsingTimer(timerSample, parser.getSourceName(), true);

                                requestLogDaemon.addLog("INFO", "Reactive",
                                        String.format(" Прогресс: %d/%d - '%s' (%d мс)",
                                                current, total, vacancy.getTitle(), duration));

                                return Flux.just(vacancy);
                            } else {
                                metricsService.incrementFailedParsing(parser.getSourceName(), "parse_failed");
                                metricsService.stopParsingTimer(timerSample, parser.getSourceName(), false);

                                requestLogDaemon.addLog("ERROR", "Reactive",
                                        String.format(" Не удалось спарсить: %s", url));
                            }
                        } else {
                            metricsService.incrementFailedParsing("unknown", "no_parser");
                            metricsService.stopParsingTimer(timerSample, "unknown", false);

                            requestLogDaemon.addLog("ERROR", "Reactive",
                                    String.format(" Нет парсера для: %s", url));
                        }
                    } catch (Exception e) {
                        metricsService.incrementFailedParsing("unknown", e.getClass().getSimpleName());
                        metricsService.stopParsingTimer(timerSample, "unknown", false);

                        requestLogDaemon.addLog("ERROR", "Reactive",
                                String.format(" Ошибка: %s", e.getMessage()));
                    }

                    metricsService.setActiveParsingTasks(activeTasks.size());
                    metricsService.setDatabaseRecords(storageService.getTotalCount());

                    return Flux.empty();
                })
                .sequential()
                .timeout(Duration.ofMinutes(5))
                .doOnComplete(() -> {
                    metricsService.setActiveParsingTasks(activeTasks.size());
                    metricsService.printMetrics();
                    requestLogDaemon.addLog("INFO", "Reactive",
                            " Реактивный парсинг завершен");
                });
    }

    private SiteParser findParserForUrl(String url) {
        return parsers.stream()
                .filter(parser -> parser.supports(url))
                .findFirst()
                .orElse(null);
    }

    private void processVacancyInParallel(Vacancy vacancy) {
        CompletableFuture.supplyAsync(() -> enrichVacancyData(vacancy), parserExecutor)
                .thenAccept(this::logEnrichedData)
                .orTimeout(10, TimeUnit.SECONDS)
                .exceptionally(throwable -> {
                    metricsService.incrementFailedParsing("parallel", "processing_error");
                    requestLogDaemon.addLog("ERROR", "Parallel",
                            String.format("Ошибка параллельной обработки: %s", throwable.getMessage()));
                    return null;
                });
    }

    private Vacancy enrichVacancyData(Vacancy vacancy) {
        // Дополнительная обработка данных вакансии
        if (vacancy.getRequirements() != null) {
            String req = vacancy.getRequirements().toLowerCase();
            if (req.contains("java")) {
                requestLogDaemon.addLog("INFO", "Analysis",
                        "Java position detected: " + vacancy.getTitle());
            }
            if (req.contains("spring")) {
                requestLogDaemon.addLog("INFO", "Analysis",
                        "Spring framework required: " + vacancy.getTitle());
            }
        }
        return vacancy;
    }

    private void logEnrichedData(Vacancy vacancy) {
        // Логирование обогащенных данных
        requestLogDaemon.addLog("DEBUG", "Enrich",
                String.format("Вакансия обогащена: %s (%s)",
                        vacancy.getTitle(), vacancy.getCompany()));
    }

    public ParserTask getTaskStatus(String taskId) {
        ParserTask task = activeTasks.get(taskId);
        if (task != null) {
            requestLogDaemon.addLog("DEBUG", "Tasks",
                    String.format("Запрос статуса задачи %s: %s",
                            taskId.substring(0, 8), task.getStatus()));
        }
        return task;
    }

    public List<ParserTask> getAllTasks() {
        List<ParserTask> tasks = List.copyOf(activeTasks.values());
        requestLogDaemon.addLog("DEBUG", "Tasks",
                String.format("Запрос списка задач: %d активных", tasks.size()));
        return tasks;
    }

    public void removeCompletedTask(String taskId) {
        ParserTask task = activeTasks.remove(taskId);
        if (task != null) {
            metricsService.setActiveParsingTasks(activeTasks.size());
            requestLogDaemon.addLog("INFO", "Tasks",
                    String.format("Удалена задача %s со статусом %s",
                            taskId.substring(0, 8), task.getStatus()));
        }
    }

    public int getTotalParsedCount() {
        return totalParsedCount.get();
    }

    public void clearCompletedTasks() {
        int beforeCount = activeTasks.size();
        activeTasks.entrySet().removeIf(entry ->
                entry.getValue().isCompleted() || entry.getValue().isFailed()
        );
        metricsService.setActiveParsingTasks(activeTasks.size());

        int removedCount = beforeCount - activeTasks.size();
        if (removedCount > 0) {
            requestLogDaemon.addLog("INFO", "Tasks",
                    String.format("Очищено %d завершенных задач", removedCount));
        }
    }

    public SiteParser getParserForSource(String source) {
        return parsers.stream()
                .filter(parser -> parser.getSourceName().equals(source))
                .findFirst()
                .orElse(null);
    }
}