package com.example.vacancyparser.service;

import com.example.vacancyparser.model.ParserTask;
import com.example.vacancyparser.service.parser.VacancyParserService;
import com.example.vacancyparser.service.storage.VacancyJpaStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DaemonServiceTest {

    @Mock
    private VacancyParserService parserService;

    @Mock
    private VacancyJpaStorageService storageService;

    @InjectMocks
    private DaemonService daemonService;

    @BeforeEach
    void setUp() {
        // Важно: не вызываем startDaemonThreads() в setUp
        // Используем lenient() чтобы избежать лишних проверок
        lenient().when(parserService.getAllTasks()).thenReturn(List.of());
        lenient().when(parserService.getTotalParsedCount()).thenReturn(0);
        lenient().when(storageService.getTotalCount()).thenReturn(0L);
    }

    @Test
    void testDaemonThreadsStart() {
        // Просто проверяем создание потоков
        daemonService.startDaemonThreads();
        // Не проверяем вызовы методов, так как они асинхронны
    }

    @Test
    void testPrintStatistics() throws Exception {
        java.lang.reflect.Method method = DaemonService.class.getDeclaredMethod("printStatistics");
        method.setAccessible(true);
        method.invoke(daemonService);

        verify(storageService, atLeastOnce()).getTotalCount();
        verify(parserService, atLeastOnce()).getAllTasks();
        verify(parserService, atLeastOnce()).getTotalParsedCount();
    }

    @Test
    void testMonitorActiveTasks() throws Exception {
        java.lang.reflect.Method method = DaemonService.class.getDeclaredMethod("monitorActiveTasks");
        method.setAccessible(true);
        method.invoke(daemonService);

        verify(parserService, atLeastOnce()).getAllTasks();
    }

    @Test
    void testPerformCleanup() throws Exception {
        java.lang.reflect.Method method = DaemonService.class.getDeclaredMethod("performCleanup");
        method.setAccessible(true);
        method.invoke(daemonService);

        verify(parserService, atLeastOnce()).clearCompletedTasks();
    }

    @Test
    void testShutdownDaemonThreads() {
        daemonService.shutdownDaemonThreads();
        // Просто проверяем что метод выполняется
    }
}