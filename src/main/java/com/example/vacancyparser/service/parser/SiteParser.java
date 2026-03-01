package com.example.vacancyparser.service.parser;

import com.example.vacancyparser.model.Vacancy;

public interface SiteParser {
    String getSourceName();
    String getDomainPattern();
    Vacancy parse(String url);
    boolean supports(String url);
}