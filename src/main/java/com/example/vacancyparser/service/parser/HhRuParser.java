package com.example.vacancyparser.service.parser;

import com.example.vacancyparser.model.Vacancy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
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

        // Название
        if (root.has("name") && !root.get("name").isNull()) {
            vacancy.setTitle(root.get("name").asText());
        }

        // Компания
        if (root.has("employer") && !root.get("employer").isNull()) {
            JsonNode employer = root.get("employer");
            if (employer.has("name") && !employer.get("name").isNull()) {
                vacancy.setCompany(employer.get("name").asText());
            }
        }

        // Зарплата
        if (root.has("salary") && !root.get("salary").isNull()) {
            vacancy.setSalary(formatSalary(root.get("salary")));
        }

        // Описание и требования
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

        // Собираем требования
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

        // Город
        if (root.has("area") && !root.get("area").isNull()) {
            JsonNode area = root.get("area");
            if (area.has("name") && !area.get("name").isNull()) {
                vacancy.setCity(area.get("name").asText());
            }
        }

        // Дата публикации
        if (root.has("created_at") && !root.get("created_at").isNull()) {
            vacancy.setPostedDate(parseHhDate(root.get("created_at").asText()));
        }

        // Архивация
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
}