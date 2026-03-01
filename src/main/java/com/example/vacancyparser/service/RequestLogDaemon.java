package com.example.vacancyparser.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Service
public class RequestLogDaemon {

    private static final Logger logger = LoggerFactory.getLogger(RequestLogDaemon.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Потокобезопасная очередь для хранения логов
    private final LinkedBlockingQueue<LogEntry> logQueue = new LinkedBlockingQueue<>(1000);

    private Thread logProcessorThread;
    private volatile boolean running = true;

    @PostConstruct
    public void startLogProcessor() {
        logProcessorThread = new Thread(this::processLogs, "log-processor-daemon");
        logProcessorThread.setDaemon(true); // Делаем поток демоном
        logProcessorThread.start();

        logger.info(" Демон-поток обработки логов запущен");

        // Добавляем тестовое сообщение
        addLog("INFO", "Система", "Демон-поток логирования инициализирован");
    }

    /**
     * Метод для добавления лога в очередь
     */
    public void addLog(String level, String source, String message) {
        LogEntry entry = new LogEntry(level, source, message, LocalDateTime.now());
        boolean added = logQueue.offer(entry);

        if (!added) {
            logger.warn("Очередь логов переполнена, сообщение потеряно: {}", message);
        }
    }

    /**
     * Метод для логирования HTTP запросов
     */
    public void logRequest(String method, String path, int statusCode, long duration) {
        String message = String.format("%s %s - %d (%d ms)", method, path, statusCode, duration);
        addLog("HTTP", "RequestLogDaemon", message);
    }

    /**
     * Обработчик очереди логов (работает в демон-потоке)
     */
    private void processLogs() {
        while (running) {
            try {
                // Берем лог из очереди (блокируемся до появления элемента)
                LogEntry entry = logQueue.poll(1, TimeUnit.SECONDS);

                if (entry != null) {
                    // Обрабатываем лог (в реальном приложении здесь может быть запись в файл, БД и т.д.)
                    writeLog(entry);
                }

                // Каждую минуту выводим статистику очереди
                if (System.currentTimeMillis() % 60000 < 1000) {
                    logger.debug("Очередь логов: размер = {}, свободно = {}",
                            logQueue.size(), logQueue.remainingCapacity());
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.info("Демон-поток обработки логов прерван");
                break;
            } catch (Exception e) {
                logger.error("Ошибка в демон-потоке обработки логов: {}", e.getMessage());
            }
        }
    }

    /**
     * Запись лога (имитация)
     */
    private void writeLog(LogEntry entry) {
        // В реальном приложении здесь может быть запись в файл, БД, отправка в ELK и т.д.
        String formatted = String.format("[%s] [%s] [%s] %s",
                entry.timestamp.format(TIME_FORMATTER),
                entry.level,
                entry.source,
                entry.message);

        // Для демонстрации выводим в консоль с цветом
        if ("ERROR".equals(entry.level)) {
            System.err.println(formatted);
        } else if ("HTTP".equals(entry.level)) {
            System.out.println(formatted);
        } else {
            System.out.println(formatted);
        }
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        if (logProcessorThread != null) {
            logProcessorThread.interrupt();
            try {
                logProcessorThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        logger.info(" Демон-поток обработки логов остановлен. Необработанных логов: {}", logQueue.size());
    }

    /**
     * Внутренний класс для хранения записи лога
     */
    private static class LogEntry {
        final String level;
        final String source;
        final String message;
        final LocalDateTime timestamp;

        LogEntry(String level, String source, String message, LocalDateTime timestamp) {
            this.level = level;
            this.source = source;
            this.message = message;
            this.timestamp = timestamp;
        }
    }
}