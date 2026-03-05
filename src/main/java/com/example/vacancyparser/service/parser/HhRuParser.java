package com.example.vacancyparser.service.parser;

import com.example.vacancyparser.model.Vacancy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Component
public class HhRuParser implements SiteParser {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String userAgent;

    private static final DateTimeFormatter HH_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

    public HhRuParser(@Qualifier("hhWebClient") WebClient webClient,
                      @Value("${hh.api.user-agent:VacancyParser/1.0 (parser@example.com)}") String userAgent) {
        this.webClient = webClient;
        this.userAgent = userAgent;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
    }

    @Override
    public String getSourceName() {
        return "hh.ru";
    }

    @Override
    public String getDomainPattern() {
        return "hh.ru";
    }

    @Override
    public boolean supports(String url) {
        return url != null && (url.contains("hh.ru") || url.contains("api.hh.ru"));
    }

    @Override
    public Vacancy parse(String url) {
        String vacancyId = extractVacancyId(url);

        try {
            String response = webClient.get()
                    .uri("/vacancies/{vacancyId}", vacancyId)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        System.err.println("HH API Error: " + errorBody);
                                        return Mono.error(new RuntimeException(
                                                "HH API request failed: " + clientResponse.statusCode()));
                                    }))
                    .bodyToMono(String.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                            .filter(throwable -> throwable instanceof WebClientResponseException.ServiceUnavailable
                                    || throwable instanceof WebClientResponseException.InternalServerError))
                    .timeout(Duration.ofSeconds(30))
                    .block();

            if (response != null) {
                JsonNode root = objectMapper.readTree(response);
                return mapToVacancy(root, url);
            }
        } catch (WebClientResponseException.NotFound e) {
            System.err.println("Vacancy not found: " + url);
        } catch (Exception e) {
            System.err.println("Error parsing vacancy from hh.ru: " + e.getMessage());
        }

        return null;
    }

    /**
     * НОВЫЙ МЕТОД: Поиск вакансий по параметрам
     * @param searchParams параметры поиска (текст, город, зарплата и т.д.)
     * @return список найденных вакансий
     */
    public List<Vacancy> searchVacancies(SearchParams searchParams) {
        List<Vacancy> results = new ArrayList<>();

        try {
            // Формируем URL с параметрами поиска
            String uri = "/vacancies?" + searchParams.toQueryString();

            String response = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
                    .timeout(Duration.ofSeconds(30))
                    .block();

            if (response != null) {
                JsonNode root = objectMapper.readTree(response);
                JsonNode items = root.get("items");

                if (items != null && items.isArray()) {
                    for (JsonNode item : items) {
                        Vacancy vacancy = mapSearchResultToVacancy(item);
                        if (vacancy != null) {
                            results.add(vacancy);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error searching vacancies: " + e.getMessage());
        }

        return results;
    }

    /**
     *  Поиск вакансий с пагинацией (реактивный поток)
     */
    public Flux<Vacancy> searchVacanciesReactive(SearchParams searchParams) {
        String uri = "/vacancies?" + searchParams.toQueryString();

        return webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(String.class)
                .flatMapMany(response -> {
                    try {
                        JsonNode root = objectMapper.readTree(response);
                        JsonNode items = root.get("items");
                        JsonNode page = root.get("page");
                        JsonNode pages = root.get("pages");

                        System.out.println("Поиск вакансий: страница " + page + " из " + pages);

                        List<Flux<Vacancy>> fluxes = new ArrayList<>();

                        // Добавляем текущую страницу
                        if (items != null && items.isArray()) {
                            fluxes.add(Flux.fromIterable(items)
                                    .map(this::mapSearchResultToVacancy)
                                    .filter(v -> v != null));
                        }

                        // Рекурсивно загружаем следующие страницы
                        int currentPage = page.asInt();
                        int totalPages = pages.asInt();

                        if (currentPage < totalPages - 1) {
                            SearchParams nextParams = searchParams.withPage(currentPage + 1);
                            fluxes.add(searchVacanciesReactive(nextParams));
                        }

                        return Flux.concat(fluxes);

                    } catch (Exception e) {
                        return Flux.error(e);
                    }
                });
    }

    /**
     * Преобразование результата поиска в вакансию
     */
    private Vacancy mapSearchResultToVacancy(JsonNode item) {
        Vacancy vacancy = new Vacancy();

        // ID и URL
        if (item.has("id") && !item.get("id").isNull()) {
            vacancy.setExternalId(item.get("id").asText());
            vacancy.setUrl("https://hh.ru/vacancy/" + item.get("id").asText());
        }

        // Название
        if (item.has("name") && !item.get("name").isNull()) {
            vacancy.setTitle(item.get("name").asText());
        }

        // Компания
        if (item.has("employer") && !item.get("employer").isNull()) {
            JsonNode employer = item.get("employer");
            if (employer.has("name") && !employer.get("name").isNull()) {
                vacancy.setCompany(employer.get("name").asText());
            }
        }

        // Зарплата
        if (item.has("salary") && !item.get("salary").isNull()) {
            vacancy.setSalary(formatSalary(item.get("salary")));
        }

        // Город
        if (item.has("area") && !item.get("area").isNull()) {
            JsonNode area = item.get("area");
            if (area.has("name") && !area.get("name").isNull()) {
                vacancy.setCity(area.get("name").asText());
            }
        }

        // Требования из сниппета
        if (item.has("snippet") && !item.get("snippet").isNull()) {
            JsonNode snippet = item.get("snippet");
            StringBuilder req = new StringBuilder();

            if (snippet.has("requirement") && !snippet.get("requirement").isNull()) {
                req.append("Требования: ").append(cleanHtml(snippet.get("requirement").asText()));
            }
            if (snippet.has("responsibility") && !snippet.get("responsibility").isNull()) {
                if (req.length() > 0) req.append("\n\n");
                req.append("Обязанности: ").append(cleanHtml(snippet.get("responsibility").asText()));
            }

            vacancy.setRequirements(req.toString());
        }

        // Дата публикации
        if (item.has("published_at") && !item.get("published_at").isNull()) {
            vacancy.setPostedDate(parseHhDate(item.get("published_at").asText()));
        }

        vacancy.setSource("hh.ru");
        vacancy.setArchived(item.has("archived") && item.get("archived").asBoolean());

        return vacancy;
    }

    private String extractVacancyId(String url) {
        String cleanUrl = url.split("\\?")[0];
        String[] parts = cleanUrl.split("/");

        for (int i = parts.length - 1; i >= 0; i--) {
            String part = parts[i];
            if (part.matches("\\d+")) {
                return part;
            }
        }
        throw new IllegalArgumentException("Could not extract vacancy ID from URL: " + url);
    }

    private Vacancy mapToVacancy(JsonNode root, String url) {
        Vacancy vacancy = new Vacancy();
        vacancy.setSource("hh.ru");
        vacancy.setUrl(url);
        vacancy.setExternalId(extractVacancyId(url));

        if (root.has("name") && !root.get("name").isNull()) {
            vacancy.setTitle(root.get("name").asText());
        }

        if (root.has("employer") && !root.get("employer").isNull()) {
            JsonNode employer = root.get("employer");
            if (employer.has("name") && !employer.get("name").isNull()) {
                vacancy.setCompany(employer.get("name").asText());
            }
        }

        if (root.has("salary") && !root.get("salary").isNull()) {
            vacancy.setSalary(formatSalary(root.get("salary")));
        }

        StringBuilder fullText = new StringBuilder();
        List<String> skills = new ArrayList<>();

        if (root.has("description") && !root.get("description").isNull()) {
            String desc = cleanHtml(root.get("description").asText());
            fullText.append(desc);
            vacancy.setDescription(desc);
        }

        if (root.has("key_skills") && root.get("key_skills").isArray()) {
            for (JsonNode skill : root.get("key_skills")) {
                if (skill.has("name") && !skill.get("name").isNull()) {
                    skills.add(skill.get("name").asText());
                }
            }
            if (!skills.isEmpty()) {
                vacancy.setKeySkills(String.join(", ", skills));
            }
        }

        StringBuilder requirements = new StringBuilder();
        if (!skills.isEmpty()) {
            requirements.append("Ключевые навыки: ").append(String.join(", ", skills)).append("\n\n");
        }
        if (fullText.length() > 0) {
            requirements.append(fullText);
        } else if (root.has("snippet") && !root.get("snippet").isNull()) {
            JsonNode snippet = root.get("snippet");
            if (snippet.has("requirement") && !snippet.get("requirement").isNull()) {
                requirements.append(cleanHtml(snippet.get("requirement").asText()));
            }
        }

        vacancy.setRequirements(requirements.length() > 5000 ?
                requirements.substring(0, 5000) + "..." : requirements.toString());

        if (root.has("area") && !root.get("area").isNull()) {
            JsonNode area = root.get("area");
            if (area.has("name") && !area.get("name").isNull()) {
                vacancy.setCity(area.get("name").asText());
            }
        }

        if (root.has("created_at") && !root.get("created_at").isNull()) {
            vacancy.setPostedDate(parseHhDate(root.get("created_at").asText()));
        }

        if (root.has("archived") && !root.get("archived").isNull()) {
            vacancy.setArchived(root.get("archived").asBoolean());
        }

        return vacancy;
    }

    private String cleanHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]*>", " ")
                .replaceAll("&[a-z]+;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String formatSalary(JsonNode salary) {
        StringBuilder sb = new StringBuilder();

        if (salary.has("from") && !salary.get("from").isNull()) {
            sb.append("от ").append(salary.get("from").asInt());
        }
        if (salary.has("to") && !salary.get("to").isNull()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append("до ").append(salary.get("to").asInt());
        }
        if (salary.has("currency") && !salary.get("currency").isNull()) {
            String currency = salary.get("currency").asText();
            sb.append(" ").append(getCurrencySymbol(currency));
        }
        if (salary.has("gross") && !salary.get("gross").isNull()) {
            boolean gross = salary.get("gross").asBoolean();
            sb.append(gross ? " (до вычета налогов)" : " (на руки)");
        }

        return sb.length() > 0 ? sb.toString() : "Не указана";
    }

    private String getCurrencySymbol(String currencyCode) {
        return switch (currencyCode.toUpperCase()) {
            case "RUR", "RUB" -> "₽";
            case "USD" -> "$";
            case "EUR" -> "€";
            case "KZT" -> "₸";
            default -> currencyCode;
        };
    }

    private LocalDateTime parseHhDate(String dateStr) {
        try {
            return LocalDateTime.parse(dateStr, HH_DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            } catch (DateTimeParseException ex) {
                return LocalDateTime.now();
            }
        }
    }

    /**
     * Внутренний класс для параметров поиска
     */
    public static class SearchParams {
        private String text;
        private Integer area;
        private Integer salary;
        private String currency;
        private Boolean onlyWithSalary;
        private String experience;
        private String employment;
        private String schedule;
        private Integer page;
        private Integer perPage;

        private SearchParams() {
            this.page = 0;
            this.perPage = 20;
        }

        public static SearchParams builder() {
            return new SearchParams();
        }

        public SearchParams withText(String text) {
            this.text = text;
            return this;
        }

        public SearchParams withArea(Integer area) {
            this.area = area;
            return this;
        }

        public SearchParams withSalary(Integer salary) {
            this.salary = salary;
            return this;
        }

        public SearchParams withCurrency(String currency) {
            this.currency = currency;
            return this;
        }

        public SearchParams withOnlyWithSalary(Boolean onlyWithSalary) {
            this.onlyWithSalary = onlyWithSalary;
            return this;
        }

        public SearchParams withExperience(String experience) {
            this.experience = experience;
            return this;
        }

        public SearchParams withEmployment(String employment) {
            this.employment = employment;
            return this;
        }

        public SearchParams withSchedule(String schedule) {
            this.schedule = schedule;
            return this;
        }

        public SearchParams withPage(Integer page) {
            this.page = page;
            return this;
        }

        public SearchParams withPerPage(Integer perPage) {
            this.perPage = perPage;
            return this;
        }

        public SearchParams withPage(int page) {
            this.page = page;
            return this;
        }

        public String toQueryString() {
            StringBuilder query = new StringBuilder();

            if (text != null && !text.isEmpty()) {
                query.append("text=").append(text.replace(" ", "+")).append("&");
            }
            if (area != null) {
                query.append("area=").append(area).append("&");
            }
            if (salary != null) {
                query.append("salary=").append(salary).append("&");
            }
            if (currency != null) {
                query.append("currency=").append(currency).append("&");
            }
            if (onlyWithSalary != null) {
                query.append("only_with_salary=").append(onlyWithSalary).append("&");
            }
            if (experience != null) {
                query.append("experience=").append(experience).append("&");
            }
            if (employment != null) {
                query.append("employment=").append(employment).append("&");
            }
            if (schedule != null) {
                query.append("schedule=").append(schedule).append("&");
            }
            if (page != null) {
                query.append("page=").append(page).append("&");
            }
            if (perPage != null) {
                query.append("per_page=").append(perPage).append("&");
            }

            // Удаляем последний & если есть
            String result = query.toString();
            if (result.endsWith("&")) {
                result = result.substring(0, result.length() - 1);
            }

            return result;
        }
    }
}