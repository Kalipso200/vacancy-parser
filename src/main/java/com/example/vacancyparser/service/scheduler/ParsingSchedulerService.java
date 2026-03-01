package com.example.vacancyparser.service.scheduler;

import com.example.vacancyparser.service.parser.VacancyParserService;
import com.example.vacancyparser.service.storage.VacancyJpaStorageService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;

@Service
public class ParsingSchedulerService {
    private final VacancyParserService parserService;
    private final VacancyJpaStorageService storageService;
    private final ExecutorService scheduledExecutor;

    public ParsingSchedulerService(
            VacancyParserService parserService,
            VacancyJpaStorageService storageService,
            @Qualifier("scheduledExecutor") ExecutorService scheduledExecutor) {
        this.parserService = parserService;
        this.storageService = storageService;
        this.scheduledExecutor = scheduledExecutor;
    }

    @Scheduled(cron = "0 0 * * * *") // Каждый час
    public void hourlyParsing() {
        System.out.println("Starting hourly parsing at " + LocalDateTime.now());

        List<String> urls = Arrays.asList(
                "https://hh.ru/vacancy/78649602",
                "https://hh.ru/vacancy/78649603",
                "https://hh.ru/vacancy/78649604"
        );

        scheduledExecutor.submit(() -> {
            parserService.parseMultipleUrlsReactive(urls)
                    .collectList()
                    .subscribe(vacancies ->
                            System.out.println("Hourly parsing completed. Parsed: " + vacancies.size()));
        });
    }

    @Scheduled(fixedDelay = 300000) // Каждые 5 минут
    public void statusCheck() {
        System.out.println("Status - Total parsed: " + parserService.getTotalParsedCount() +
                ", Active tasks: " + parserService.getAllTasks().size() +
                ", DB records: " + storageService.getTotalCount());
    }

    @Scheduled(cron = "0 0 3 * * *") // Каждый день в 3 часа ночи
    public void cleanup() {
        System.out.println("Cleaning up old tasks and archived vacancies");
        parserService.clearCompletedTasks();
        storageService.deleteAllArchived();
    }
}