package com.example.vacancyparser.benchmark;

import com.example.vacancyparser.model.Vacancy;
import com.example.vacancyparser.service.parser.HhRuParser;
import com.example.vacancyparser.service.parser.SiteParser;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@State(Scope.Benchmark)
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class ParsingBenchmark {

    @Param({"100", "1000", "10000"})
    private int dataSize;

    private List<String> testData;
    private List<Vacancy> testVacancies;
    private SiteParser parser;

    @Setup(Level.Trial)
    public void setup() {
        // Создаем тестовые данные разного размера
        testData = IntStream.range(1, dataSize + 1)
                .mapToObj(i -> String.format(
                        "Vacancy %d: Java Developer with Spring Boot and Hibernate. " +
                                "Requirements: %d years of experience in Java, Spring, Microservices. " +
                                "Nice to have: Docker, Kubernetes, AWS. Company: Tech Corp %d",
                        i, i % 5 + 1, i % 10))
                .collect(Collectors.toList());

        // Создаем тестовые вакансии
        testVacancies = new ArrayList<>();
        for (int i = 0; i < dataSize; i++) {
            Vacancy v = new Vacancy();
            v.setId((long) i);
            v.setTitle("Java Developer " + i);
            v.setCompany("Company " + (i % 20));
            v.setCity(i % 3 == 0 ? "Москва" : i % 3 == 1 ? "СПб" : "Казань");
            v.setSalary("от " + (100000 + i * 1000) + " ₽");
            v.setRequirements("Java, Spring, Hibernate, Docker, Kubernetes, Microservices");
            v.setKeySkills("Java, Spring, Docker");
            v.setSource(i % 4 == 0 ? "hh.ru" : "superjob.ru");
            testVacancies.add(v);
        }

        // Инициализируем парсер (без реальных HTTP вызовов)
        WebClient webClient = WebClient.builder()
                .baseUrl("https://api.hh.ru")
                .build();
        parser = new HhRuParser(webClient, "TestAgent/1.0 (benchmark@example.com)");
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void processWithForLoop(Blackhole blackhole) {
        List<String> results = new ArrayList<>();
        for (int i = 0; i < testData.size(); i++) {
            String processed = simulateTextProcessing(testData.get(i));
            results.add(processed);
        }
        blackhole.consume(results);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void processWithStream(Blackhole blackhole) {
        List<String> results = testData.stream()
                .map(this::simulateTextProcessing)
                .collect(Collectors.toList());
        blackhole.consume(results);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void processWithParallelStream(Blackhole blackhole) {
        List<String> results = testData.parallelStream()
                .map(this::simulateTextProcessing)
                .collect(Collectors.toList());
        blackhole.consume(results);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void processWithCompletableFuture(Blackhole blackhole) {
        List<CompletableFuture<String>> futures = testData.stream()
                .map(data -> CompletableFuture.supplyAsync(() -> simulateTextProcessing(data)))
                .collect(Collectors.toList());

        List<String> results = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        blackhole.consume(results);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void throughputForLoop(Blackhole blackhole) {
        List<String> results = new ArrayList<>();
        for (int i = 0; i < testData.size(); i++) {
            results.add(simulateLightProcessing(testData.get(i)));
        }
        blackhole.consume(results);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void throughputStream(Blackhole blackhole) {
        List<String> results = testData.stream()
                .map(this::simulateLightProcessing)
                .collect(Collectors.toList());
        blackhole.consume(results);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void throughputParallelStream(Blackhole blackhole) {
        List<String> results = testData.parallelStream()
                .map(this::simulateLightProcessing)
                .collect(Collectors.toList());
        blackhole.consume(results);
    }

    /**
     * Симуляция тяжелой текстовой обработки (регулярные выражения, манипуляции со строками)
     */
    private String simulateTextProcessing(String input) {
        StringBuilder result = new StringBuilder();

        // Инверсия регистра
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isUpperCase(c)) {
                result.append(Character.toLowerCase(c));
            } else if (Character.isLowerCase(c)) {
                result.append(Character.toUpperCase(c));
            } else {
                result.append(c);
            }
        }

        // Поиск ключевых слов
        String lowerInput = input.toLowerCase();
        if (lowerInput.contains("java")) result.append(" [JAVA]");
        if (lowerInput.contains("spring")) result.append(" [SPRING]");
        if (lowerInput.contains("docker")) result.append(" [DOCKER]");
        if (lowerInput.contains("kubernetes")) result.append(" [K8S]");
        if (lowerInput.contains("aws")) result.append(" [AWS]");

        // Удаление лишних пробелов
        return result.toString().replaceAll("\\s+", " ").trim();
    }

    /**
     * Симуляция легкой обработки (для тестов пропускной способности)
     */
    private String simulateLightProcessing(String input) {
        if (input == null) return "";
        return input.toLowerCase().replaceAll("[^a-zA-Z0-9]", " ").trim();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        testData = null;
        testVacancies = null;
        System.gc();
    }
}