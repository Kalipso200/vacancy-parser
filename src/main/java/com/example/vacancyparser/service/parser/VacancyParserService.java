package com.example.vacancyparser.service.parser;

import com.example.vacancyparser.model.ParserTask;
import com.example.vacancyparser.model.Vacancy;
import com.example.vacancyparser.service.RequestLogDaemon;
import com.example.vacancyparser.service.storage.VacancyJpaStorageService;
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
    private final ConcurrentHashMap<String, ParserTask> activeTasks = new ConcurrentHashMap<>();
    private final AtomicInteger totalParsedCount = new AtomicInteger(0);

    public VacancyParserService(
            @Qualifier("parserExecutor") Executor parserExecutor,
            ForkJoinPool forkJoinPool,
            List<SiteParser> parsers,
            VacancyJpaStorageService storageService,
            RequestLogDaemon requestLogDaemon) {
        this.parserExecutor = parserExecutor;
        this.forkJoinPool = forkJoinPool;
        this.parsers = parsers;
        this.storageService = storageService;
        this.requestLogDaemon = requestLogDaemon;

        // Логируем инициализацию сервиса
        requestLogDaemon.addLog("INFO", "VacancyParserService",
                "Сервис парсинга инициализирован с " + parsers.size() + " парсерами");
    }

    public ParserTask parseVacancyAsync(String url) {
        SiteParser parser = findParserForUrl(url);
        if (parser == null) {
            String error = "No parser found for URL: " + url;
            requestLogDaemon.addLog("ERROR", "Parser", error);
            throw new IllegalArgumentException(error);
        }

        ParserTask task = new ParserTask(url, parser.getSourceName());

        // Логируем создание задачи
        requestLogDaemon.addLog("INFO", "Parser",
                String.format("Создана задача парсинга: %s для URL: %s",
                        task.getTaskId().substring(0, 8), url));

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            task.setStatus(ParserTask.ParserStatus.RUNNING);
            requestLogDaemon.addLog("DEBUG", "Parser",
                    String.format("Задача %s начала выполнение", task.getTaskId().substring(0, 8)));

            try {
                long startTime = System.currentTimeMillis();
                Vacancy vacancy = parser.parse(url);
                long duration = System.currentTimeMillis() - startTime;

                if (vacancy != null) {
                    storageService.save(vacancy);
                    task.setParsedCount(1);
                    totalParsedCount.incrementAndGet();

                    // Логируем успешный парсинг
                    requestLogDaemon.addLog("INFO", "Parser",
                            String.format(" Задача %s: успешно спарсена вакансия '%s' из %s (время: %d мс)",
                                    task.getTaskId().substring(0, 8),
                                    vacancy.getTitle(),
                                    vacancy.getSource(),
                                    duration));

                    // Параллельная обработка с использованием ForkJoinPool
                    forkJoinPool.submit(() -> processVacancyInParallel(vacancy)).join();

                    task.setStatus(ParserTask.ParserStatus.COMPLETED);

                    requestLogDaemon.addLog("DEBUG", "Parser",
                            String.format("Задача %s завершена", task.getTaskId().substring(0, 8)));

                } else {
                    task.setStatus(ParserTask.ParserStatus.FAILED);
                    task.setErrorMessage("Failed to parse vacancy");

                    // Логируем ошибку парсинга
                    requestLogDaemon.addLog("ERROR", "Parser",
                            String.format(" Задача %s: не удалось спарсить вакансию (время: %d мс)",
                                    task.getTaskId().substring(0, 8), duration));
                }
            } catch (Exception e) {
                task.setStatus(ParserTask.ParserStatus.FAILED);
                task.setErrorMessage(e.getMessage());

                // Логируем исключение
                requestLogDaemon.addLog("ERROR", "Parser",
                        String.format(" Задача %s: исключение при парсинге - %s",
                                task.getTaskId().substring(0, 8), e.getMessage()));
                e.printStackTrace();
            }
        }, parserExecutor);

        task.setFuture(future);
        activeTasks.put(task.getTaskId(), task);

        return task;
    }

    public List<ParserTask> parseMultipleVacancies(List<String> urls) {
        requestLogDaemon.addLog("INFO", "Parser",
                String.format("Запуск множественного парсинга: %d URL", urls.size()));

        List<ParserTask> tasks = urls.stream()
                .map(this::parseVacancyAsync)
                .toList();

        requestLogDaemon.addLog("DEBUG", "Parser",
                String.format("Создано %d задач для множественного парсинга", tasks.size()));

        return tasks;
    }

    public Flux<Vacancy> parseMultipleUrlsReactive(List<String> urls) {
        AtomicInteger counter = new AtomicInteger(0);
        int total = urls.size();

        requestLogDaemon.addLog("INFO", "Parser",
                String.format(" Запуск реактивного парсинга: %d URL", total));

        return Flux.fromIterable(urls)
                .parallel()
                .runOn(Schedulers.fromExecutor(parserExecutor))
                .flatMap(url -> {
                    try {
                        SiteParser parser = findParserForUrl(url);
                        if (parser != null) {
                            long startTime = System.currentTimeMillis();
                            Vacancy vacancy = parser.parse(url);
                            long duration = System.currentTimeMillis() - startTime;

                            if (vacancy != null) {
                                storageService.save(vacancy);
                                int current = counter.incrementAndGet();

                                // Логируем прогресс
                                requestLogDaemon.addLog("INFO", "Reactive",
                                        String.format(" Прогресс: %d/%d - спарсена '%s' (%d мс)",
                                                current, total, vacancy.getTitle(), duration));

                                System.out.printf("Progress: %d/%d vacancies parsed%n", current, total);
                                return Flux.just(vacancy);
                            }
                        }
                    } catch (Exception e) {
                        requestLogDaemon.addLog("ERROR", "Reactive",
                                String.format(" Ошибка парсинга %s: %s", url, e.getMessage()));
                        System.err.println("Error parsing " + url + ": " + e.getMessage());
                    }
                    return Flux.empty();
                })
                .sequential()
                .timeout(Duration.ofMinutes(5))
                .doOnComplete(() -> {
                    requestLogDaemon.addLog("INFO", "Reactive",
                            " Реактивный парсинг завершен. Всего спарсено: " + counter.get());
                    System.out.println("Reactive parsing completed");
                });
    }

    private SiteParser findParserForUrl(String url) {
        return parsers.stream()
                .filter(parser -> parser.supports(url))
                .findFirst()
                .orElse(null);
    }

    private void processVacancyInParallel(Vacancy vacancy) {
        // Параллельная обработка данных вакансии
        CompletableFuture.supplyAsync(() -> enrichVacancyData(vacancy), parserExecutor)
                .thenApply(this::analyzeRequirements)
                .thenAccept(this::logEnrichedData)
                .orTimeout(10, TimeUnit.SECONDS)
                .exceptionally(throwable -> {
                    requestLogDaemon.addLog("ERROR", "Parallel",
                            "Ошибка параллельной обработки: " + throwable.getMessage());
                    System.err.println("Error processing vacancy: " + throwable.getMessage());
                    return null;
                });
    }

    private Vacancy enrichVacancyData(Vacancy vacancy) {
        // Дополнительное обогащение данных
        if (vacancy.getRequirements() != null) {
            String req = vacancy.getRequirements().toLowerCase();
            if (req.contains("java")) {
                requestLogDaemon.addLog("INFO", "Analysis",
                        "Java позиция: " + vacancy.getTitle());
                System.out.println("Java position: " + vacancy.getTitle());
            }
            if (req.contains("spring")) {
                requestLogDaemon.addLog("INFO", "Analysis",
                        "Spring required: " + vacancy.getTitle());
                System.out.println("Spring required: " + vacancy.getTitle());
            }
            if (req.contains("python")) {
                requestLogDaemon.addLog("INFO", "Analysis",
                        "Python position: " + vacancy.getTitle());
                System.out.println("Python position: " + vacancy.getTitle());
            }
        }
        return vacancy;
    }

    private Vacancy analyzeRequirements(Vacancy vacancy) {
        // Анализ требований
        return vacancy;
    }

    private void logEnrichedData(Vacancy vacancy) {
        String message = String.format("Обогащена вакансия: %s от %s",
                vacancy.getTitle(), vacancy.getCompany());
        requestLogDaemon.addLog("DEBUG", "Enrich", message);
        System.out.println("Enriched: " + vacancy.getTitle() + " from " + vacancy.getCompany());
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
        int removedCount = beforeCount - activeTasks.size();

        if (removedCount > 0) {
            requestLogDaemon.addLog("INFO", "Tasks",
                    String.format("Очищено %d завершенных задач", removedCount));
        }
    }
}