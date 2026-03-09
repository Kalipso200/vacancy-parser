package com.example.vacancyparser.service;

import com.example.vacancyparser.model.Vacancy;
import com.example.vacancyparser.service.parser.HhRuParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class HhRuParserTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private ObjectMapper objectMapper;
    private HhRuParser parser;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        parser = new HhRuParser(webClient, "TestAgent/1.0 (test@example.com)");
    }

    @Test
    void testGetSourceName() {
        assertThat(parser.getSourceName()).isEqualTo("hh.ru");
    }

    @Test
    void testGetDomainPattern() {
        assertThat(parser.getDomainPattern()).isEqualTo("hh.ru");
    }

    @Test
    void testSupports() {
        assertThat(parser.supports("https://hh.ru/vacancy/131058232")).isTrue();
        assertThat(parser.supports("https://api.hh.ru/vacancies/131058236")).isTrue();
        assertThat(parser.supports("https://superjob.ru/vacancy/131058232")).isFalse();
        assertThat(parser.supports(null)).isFalse();
    }

    @Test
    void testExtractVacancyId() throws Exception {
        Method method = HhRuParser.class.getDeclaredMethod("extractVacancyId", String.class);
        method.setAccessible(true);

        String url1 = "https://api.hh.ru/vacancy/131058236";
        String id1 = (String) method.invoke(parser, url1);
        assertThat(id1).isEqualTo("131058236");

        String url2 = "https://api.hh.ru/vacancy/131058235?query=param";
        String id2 = (String) method.invoke(parser, url2);
        assertThat(id2).isEqualTo("131058235");
    }
}