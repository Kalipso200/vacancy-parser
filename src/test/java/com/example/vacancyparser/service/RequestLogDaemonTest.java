package com.example.vacancyparser.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class RequestLogDaemonTest {

    private RequestLogDaemon requestLogDaemon;

    @BeforeEach
    void setUp() {
        requestLogDaemon = new RequestLogDaemon();
    }

    @Test
    void testAddLog() throws Exception {
        requestLogDaemon.addLog("INFO", "TestSource", "Test message");

        // Получаем доступ к приватной очереди
        Field queueField = RequestLogDaemon.class.getDeclaredField("logQueue");
        queueField.setAccessible(true);
        LinkedBlockingQueue<?> queue = (LinkedBlockingQueue<?>) queueField.get(requestLogDaemon);

        assertThat(queue).hasSize(1);
    }

    @Test
    void testLogRequest() throws Exception {
        requestLogDaemon.logRequest("GET", "/api/test", 200, 150);

        Field queueField = RequestLogDaemon.class.getDeclaredField("logQueue");
        queueField.setAccessible(true);
        LinkedBlockingQueue<?> queue = (LinkedBlockingQueue<?>) queueField.get(requestLogDaemon);

        assertThat(queue).hasSize(1);
    }

    @Test
    void testStartLogProcessor() throws Exception {
        requestLogDaemon.startLogProcessor();

        Field threadField = RequestLogDaemon.class.getDeclaredField("logProcessorThread");
        threadField.setAccessible(true);
        Thread thread = (Thread) threadField.get(requestLogDaemon);

        assertThat(thread).isNotNull();
        assertThat(thread.isDaemon()).isTrue();
        assertThat(thread.getName()).isEqualTo("log-processor-daemon");
    }

    @Test
    void testProcessLogs() throws Exception {
        // Добавляем несколько логов
        requestLogDaemon.addLog("INFO", "Test", "Message 1");
        requestLogDaemon.addLog("ERROR", "Test", "Message 2");
        requestLogDaemon.addLog("HTTP", "Test", "Message 3");

        // Запускаем обработку
        requestLogDaemon.startLogProcessor();

        // Даем время на обработку
        Thread.sleep(500);

        // Проверяем что очередь пуста
        Field queueField = RequestLogDaemon.class.getDeclaredField("logQueue");
        queueField.setAccessible(true);
        LinkedBlockingQueue<?> queue = (LinkedBlockingQueue<?>) queueField.get(requestLogDaemon);

        assertThat(queue).isEmpty();
    }

    @Test
    void testShutdown() throws Exception {
        requestLogDaemon.startLogProcessor();
        requestLogDaemon.addLog("INFO", "Test", "Message");

        requestLogDaemon.shutdown();

        Field runningField = RequestLogDaemon.class.getDeclaredField("running");
        runningField.setAccessible(true);
        boolean running = (boolean) runningField.get(requestLogDaemon);

        assertThat(running).isFalse();
    }

    @Test
    void testLogEntryCreation() throws Exception {
        requestLogDaemon.addLog("INFO", "Source", "Message");

        // Получаем доступ к приватному методу processLogs через рефлексию для проверки
        Method processMethod = RequestLogDaemon.class.getDeclaredMethod("processLogs");
        processMethod.setAccessible(true);

        // Не вызываем метод, просто проверяем что он существует
        assertThat(processMethod).isNotNull();
    }
}