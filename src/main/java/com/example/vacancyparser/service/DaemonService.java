package com.example.vacancyparser.service;

import com.example.vacancyparser.service.parser.VacancyParserService;
import com.example.vacancyparser.service.storage.VacancyJpaStorageService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class DaemonService {

    private static final Logger logger = LoggerFactory.getLogger(DaemonService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final VacancyParserService parserService;
    private final VacancyJpaStorageService storageService;

    private ScheduledExecutorService monitorExecutor;
    private ScheduledExecutorService statsExecutor;
    private ScheduledExecutorService cleanupExecutor;

    private Thread statsDaemonThread;
    private Thread monitorDaemonThread;

    public DaemonService(VacancyParserService parserService,
                         VacancyJpaStorageService storageService) {
        this.parserService = parserService;
        this.storageService = storageService;
    }

    @PostConstruct
    public void startDaemonThreads() {
        logger.info(" Запуск демон-потоков для мониторинга и логирования");

        // Способ 1: Создание демон-потока через Thread
        startStatsDaemonThread();

        // Способ 2: Создание демон-потока через Runnable
        startMonitorDaemonThread();

        // Способ 3: Использование ScheduledExecutorService с демон-потоками
        startScheduledDaemons();
    }

    /**
     * Способ 1: Демон-поток через Thread
     * Выводит статистику каждые 10 секунд
     */
    private void startStatsDaemonThread() {
        statsDaemonThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    printStatistics();
                    TimeUnit.SECONDS.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.info(" Демон-поток статистики прерван");
                    break;
                } catch (Exception e) {
                    logger.error("Ошибка в демон-потоке статистики: {}", e.getMessage());
                }
            }
        });

        // Устанавливаем как демон-поток
        statsDaemonThread.setDaemon(true);
        statsDaemonThread.setName("stats-daemon");
        statsDaemonThread.start();

        logger.info(" Демон-поток статистики запущен: {}", statsDaemonThread.getName());
    }

    /**
     * Способ 2: Демон-поток через Runnable
     * Мониторит активные задачи каждые 5 секунд
     */
    private void startMonitorDaemonThread() {
        Runnable monitorTask = () -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    monitorActiveTasks();
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.info("Демон-поток мониторинга прерван");
                    break;
                } catch (Exception e) {
                    logger.error("Ошибка в демон-потоке мониторинга: {}", e.getMessage());
                }
            }
        };

        monitorDaemonThread = new Thread(monitorTask);
        monitorDaemonThread.setDaemon(true);
        monitorDaemonThread.setName("monitor-daemon");
        monitorDaemonThread.start();

        logger.info(" Демон-поток мониторинга запущен: {}", monitorDaemonThread.getName());
    }

    /**
     * Способ 3: ScheduledExecutorService с демон-потоками
     * Использует фабрику демон-потоков
     */
    private void startScheduledDaemons() {
        // Создаем ExecutorService с демон-потоками
        monitorExecutor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("daemon-scheduled-" + t.getId());
            return t;
        });

        // Задача для логирования состояния памяти
        monitorExecutor.scheduleAtFixedRate(() -> {
            logMemoryStats();
        }, 0, 30, TimeUnit.SECONDS);

        // Задача для очистки старых логов (имитация)
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("cleanup-daemon");
            return t;
        });

        cleanupExecutor.scheduleAtFixedRate(() -> {
            performCleanup();
        }, 1, 5, TimeUnit.MINUTES);

        // Еще один демон для сбора метрик
        statsExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("metrics-daemon");
            return t;
        });

        statsExecutor.scheduleAtFixedRate(() -> {
            collectMetrics();
        }, 0, 15, TimeUnit.SECONDS);
    }

    /**
     * Вывод статистики (для демон-потока)
     */
    private void printStatistics() {
        String time = LocalDateTime.now().format(TIME_FORMATTER);
        long totalVacancies = storageService.getTotalCount();
        int activeTasks = parserService.getAllTasks().size();
        int totalParsed = parserService.getTotalParsedCount();

        String stats = String.format("[%s] Статистика: вакансий: %d, активных задач: %d, всего спарсено: %d",
                time, totalVacancies, activeTasks, totalParsed);

        System.out.println(stats);
        logger.info(stats);
    }

    /**
     * Мониторинг активных задач (для демон-потока)
     */
    private void monitorActiveTasks() {
        var tasks = parserService.getAllTasks();
        if (tasks.isEmpty()) {
            return;
        }

        String time = LocalDateTime.now().format(TIME_FORMATTER);
        long runningCount = tasks.stream().filter(t -> t.isRunning()).count();
        long completedCount = tasks.stream().filter(t -> t.isCompleted()).count();
        long failedCount = tasks.stream().filter(t -> t.isFailed()).count();

        String monitor = String.format("[%s] Мониторинг задач: всего: %d, выполняется: %d, завершено: %d, ошибок: %d",
                time, tasks.size(), runningCount, completedCount, failedCount);

        System.out.println(monitor);

        // Логируем детали по каждой активной задаче
        tasks.stream()
                .filter(t -> t.isRunning())
                .forEach(t -> logger.debug("  Задача {}: {}", t.getTaskId().substring(0, 8), t.getUrl()));
    }

    /**
     * Логирование состояния памяти
     */
    private void logMemoryStats() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        long freeMemory = runtime.freeMemory();

        double usedPercent = (usedMemory * 100.0) / maxMemory;

        logger.info(" Память - используемая: {} MB ({}%), свободная: {} MB, максимум: {} MB",
                bytesToMB(usedMemory),
                String.format("%.1f", usedPercent),
                bytesToMB(freeMemory),
                bytesToMB(maxMemory));
    }

    /**
     * Очистка (имитация работы)
     */
    private void performCleanup() {
        String time = LocalDateTime.now().format(TIME_FORMATTER);
        logger.info(" [{}] Запуск плановой очистки...", time);

        // Имитация работы
        try {
            // Проверяем и очищаем завершенные задачи
            int beforeCount = parserService.getAllTasks().size();
            parserService.clearCompletedTasks();
            int afterCount = parserService.getAllTasks().size();

            if (beforeCount != afterCount) {
                logger.info("  Очищено {} завершенных задач", beforeCount - afterCount);
            }

        } catch (Exception e) {
            logger.error("  Ошибка при очистке: {}", e.getMessage());
        }
    }

    /**
     * Сбор метрик (имитация)
     */
    private void collectMetrics() {
        String time = LocalDateTime.now().format(TIME_FORMATTER);

        // Собираем метрики по источникам
        var bySource = storageService.getStatsBySource();
        var byCity = storageService.getStatsByCity();

        logger.debug(" [{}] Метрики - по источникам: {}, по городам: {}",
                time, bySource.size(), byCity.size());
    }

    /**
     * Конвертер байт в мегабайты
     */
    private long bytesToMB(long bytes) {
        return bytes / (1024 * 1024);
    }

    @PreDestroy
    public void shutdownDaemonThreads() {
        logger.info(" Остановка демон-потоков...");

        // Прерываем поток статистики
        if (statsDaemonThread != null) {
            statsDaemonThread.interrupt();
        }

        // Прерываем поток мониторинга
        if (monitorDaemonThread != null) {
            monitorDaemonThread.interrupt();
        }

        // Останавливаем ExecutorService
        if (monitorExecutor != null) {
            monitorExecutor.shutdownNow();
        }
        if (cleanupExecutor != null) {
            cleanupExecutor.shutdownNow();
        }
        if (statsExecutor != null) {
            statsExecutor.shutdownNow();
        }

        logger.info("Все демон-потоки остановлены");
    }
}