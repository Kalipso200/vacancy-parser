package com.example.vacancyparser.benchmark;

import com.example.vacancyparser.model.Vacancy;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@State(Scope.Benchmark)
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class VacancyProcessingBenchmark {

    @Param({"100", "1000", "10000"})
    private int size;

    private List<Vacancy> vacancies;
    private List<Vacancy> vacanciesForFilter;
    private Map<String, List<Vacancy>> expectedGrouping;

    @Setup(Level.Trial)
    public void setup() {
        vacancies = new ArrayList<>();
        Random random = new Random(42); // Фиксированный seed для воспроизводимости

        String[] cities = {"Москва", "Санкт-Петербург", "Казань", "Екатеринбург", "Новосибирск"};
        String[] companies = {"Яндекс", "Тинькофф", "Сбер", "VK", "Ozon", "Wildberries", "Avito"};
        String[] sources = {"hh.ru", "superjob.ru", "habr.career"};
        String[] skills = {"Java", "Python", "JavaScript", "C++", "Go", "Rust", "Kotlin", "Swift"};

        for (int i = 0; i < size; i++) {
            Vacancy v = new Vacancy();
            v.setId((long) i);
            v.setTitle(generateTitle(random, skills));
            v.setCompany(companies[random.nextInt(companies.length)]);
            v.setCity(cities[random.nextInt(cities.length)]);
            v.setSalary(generateSalary(random));
            v.setRequirements(generateRequirements(random, skills));
            v.setKeySkills(generateKeySkills(random, skills));
            v.setSource(sources[random.nextInt(sources.length)]);
            v.setPostedDate(LocalDateTime.now().minusDays(random.nextInt(30)));
            v.setArchived(random.nextBoolean());
            vacancies.add(v);
        }

        // Создаем копию для тестов фильтрации
        vacanciesForFilter = new ArrayList<>(vacancies);

        // Ожидаемый результат для группировки (только для проверки)
        expectedGrouping = vacancies.stream()
                .collect(Collectors.groupingBy(Vacancy::getSource));
    }

    private String generateTitle(Random random, String[] skills) {
        String[] roles = {"Developer", "Engineer", "Architect", "Lead", "Specialist"};
        return skills[random.nextInt(skills.length)] + " " +
                roles[random.nextInt(roles.length)];
    }

    private String generateSalary(Random random) {
        int from = 50000 + random.nextInt(150000);
        int to = from + random.nextInt(100000);
        String[] currencies = {"₽", "$", "€"};
        return "от " + from + " до " + to + " " + currencies[random.nextInt(currencies.length)];
    }

    private String generateRequirements(Random random, String[] skills) {
        int count = 2 + random.nextInt(4);
        List<String> selected = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            selected.add(skills[random.nextInt(skills.length)]);
        }
        return String.join(", ", selected) + ", опыт работы " + (1 + random.nextInt(5)) + "+ лет";
    }

    private String generateKeySkills(Random random, String[] skills) {
        int count = 1 + random.nextInt(3);
        List<String> selected = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            selected.add(skills[random.nextInt(skills.length)]);
        }
        return String.join(", ", selected);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void filterAndSortImperative(Blackhole blackhole) {
        List<Vacancy> result = new ArrayList<>();

        // Фильтрация
        for (Vacancy v : vacancies) {
            if (v.getCity().equals("Москва") && !v.isArchived()) {
                result.add(v);
            }
        }

        // Сортировка
        result.sort((v1, v2) -> {
            if (v1.getPostedDate() == null && v2.getPostedDate() == null) return 0;
            if (v1.getPostedDate() == null) return 1;
            if (v2.getPostedDate() == null) return -1;
            return v2.getPostedDate().compareTo(v1.getPostedDate()); // по убыванию
        });

        blackhole.consume(result);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void filterAndSortStream(Blackhole blackhole) {
        List<Vacancy> result = vacancies.stream()
                .filter(v -> "Москва".equals(v.getCity()))
                .filter(v -> !v.isArchived())
                .sorted(Comparator.comparing(Vacancy::getPostedDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        blackhole.consume(result);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void filterAndSortParallelStream(Blackhole blackhole) {
        List<Vacancy> result = vacancies.parallelStream()
                .filter(v -> "Москва".equals(v.getCity()))
                .filter(v -> !v.isArchived())
                .sorted(Comparator.comparing(Vacancy::getPostedDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        blackhole.consume(result);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void groupBySourceImperative(Blackhole blackhole) {
        Map<String, List<Vacancy>> bySource = new HashMap<>();

        for (Vacancy v : vacancies) {
            String source = v.getSource();
            if (!bySource.containsKey(source)) {
                bySource.put(source, new ArrayList<>());
            }
            bySource.get(source).add(v);
        }

        blackhole.consume(bySource);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void groupBySourceStream(Blackhole blackhole) {
        Map<String, List<Vacancy>> bySource = vacancies.stream()
                .collect(Collectors.groupingBy(Vacancy::getSource));

        blackhole.consume(bySource);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void groupBySourceParallelStream(Blackhole blackhole) {
        Map<String, List<Vacancy>> bySource = vacancies.parallelStream()
                .collect(Collectors.groupingByConcurrent(Vacancy::getSource));

        blackhole.consume(bySource);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void complexFilterImperative(Blackhole blackhole) {
        List<Vacancy> result = new ArrayList<>();

        for (Vacancy v : vacancies) {
            if (v.getCity().equals("Москва") &&
                    v.getSalary() != null &&
                    v.getSalary().contains("от") &&
                    v.getRequirements() != null &&
                    v.getRequirements().toLowerCase().contains("java")) {
                result.add(v);
            }
        }

        blackhole.consume(result);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void complexFilterStream(Blackhole blackhole) {
        List<Vacancy> result = vacancies.stream()
                .filter(v -> "Москва".equals(v.getCity()))
                .filter(v -> v.getSalary() != null)
                .filter(v -> v.getSalary().contains("от"))
                .filter(v -> v.getRequirements() != null)
                .filter(v -> v.getRequirements().toLowerCase().contains("java"))
                .collect(Collectors.toList());

        blackhole.consume(result);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void complexFilterParallelStream(Blackhole blackhole) {
        List<Vacancy> result = vacancies.parallelStream()
                .filter(v -> "Москва".equals(v.getCity()))
                .filter(v -> v.getSalary() != null)
                .filter(v -> v.getSalary().contains("от"))
                .filter(v -> v.getRequirements() != null)
                .filter(v -> v.getRequirements().toLowerCase().contains("java"))
                .collect(Collectors.toList());

        blackhole.consume(result);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void throughputFilterImperative(Blackhole blackhole) {
        List<Vacancy> result = new ArrayList<>();
        for (Vacancy v : vacanciesForFilter) {
            if (!v.isArchived() && v.getSalary() != null) {
                result.add(v);
            }
        }
        blackhole.consume(result);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void throughputFilterStream(Blackhole blackhole) {
        List<Vacancy> result = vacanciesForFilter.stream()
                .filter(v -> !v.isArchived())
                .filter(v -> v.getSalary() != null)
                .collect(Collectors.toList());
        blackhole.consume(result);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void throughputFilterParallelStream(Blackhole blackhole) {
        List<Vacancy> result = vacanciesForFilter.parallelStream()
                .filter(v -> !v.isArchived())
                .filter(v -> v.getSalary() != null)
                .collect(Collectors.toList());
        blackhole.consume(result);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void statisticsImperative(Blackhole blackhole) {
        Map<String, Long> stats = new HashMap<>();

        for (Vacancy v : vacancies) {
            String source = v.getSource();
            stats.put(source, stats.getOrDefault(source, 0L) + 1);
        }

        blackhole.consume(stats);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void statisticsStream(Blackhole blackhole) {
        Map<String, Long> stats = vacancies.stream()
                .collect(Collectors.groupingBy(Vacancy::getSource, Collectors.counting()));

        blackhole.consume(stats);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void statisticsParallelStream(Blackhole blackhole) {
        Map<String, Long> stats = vacancies.parallelStream()
                .collect(Collectors.groupingByConcurrent(Vacancy::getSource, Collectors.counting()));

        blackhole.consume(stats);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        vacancies = null;
        vacanciesForFilter = null;
        expectedGrouping = null;
        System.gc();
    }
}