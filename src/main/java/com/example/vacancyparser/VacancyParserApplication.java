package com.example.vacancyparser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class VacancyParserApplication {
    public static void main(String[] args) {
        SpringApplication.run(VacancyParserApplication.class, args);
        System.out.println("==========================================");
        System.out.println("Vacancy Parser started on port 8080");
        System.out.println("H2 Console: http://localhost:8080/h2-console");
        System.out.println("API: http://localhost:8080/api/vacancies");
        System.out.println("Swagger UI: http://localhost:8080/swagger-ui.html");
        System.out.println("==========================================");
    }
}