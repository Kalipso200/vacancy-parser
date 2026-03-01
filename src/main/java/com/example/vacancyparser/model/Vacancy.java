package com.example.vacancyparser.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "vacancies", indexes = {
        @Index(name = "idx_company", columnList = "company"),
        @Index(name = "idx_city", columnList = "city"),
        @Index(name = "idx_posted_date", columnList = "postedDate"),
        @Index(name = "idx_source", columnList = "source"),
        @Index(name = "idx_external_id", columnList = "externalId")
})
public class Vacancy implements Comparable<Vacancy> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 255)
    private String company;

    @Column(length = 255)
    private String salary;

    @Column(columnDefinition = "TEXT")
    private String requirements;

    @Column(length = 255)
    private String city;

    @Column(name = "posted_date")
    private LocalDateTime postedDate;

    @Column(length = 50)
    private String source;

    @Column(length = 500)
    private String url;

    @Column(name = "parsed_date")
    private LocalDateTime parsedDate;

    @Column(name = "archived", nullable = false)
    private boolean archived = false;

    @Column(name = "external_id", length = 100)
    private String externalId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 1000)
    private String keySkills;

    public Vacancy() {
        this.parsedDate = LocalDateTime.now();
    }

    // Геттеры
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getCompany() { return company; }
    public String getSalary() { return salary; }
    public String getRequirements() { return requirements; }
    public String getCity() { return city; }
    public LocalDateTime getPostedDate() { return postedDate; }
    public String getSource() { return source; }
    public String getUrl() { return url; }
    public LocalDateTime getParsedDate() { return parsedDate; }
    public boolean isArchived() { return archived; }
    public String getExternalId() { return externalId; }
    public String getDescription() { return description; }
    public String getKeySkills() { return keySkills; }

    // Сеттеры
    public void setId(Long id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setCompany(String company) { this.company = company; }
    public void setSalary(String salary) { this.salary = salary; }
    public void setRequirements(String requirements) { this.requirements = requirements; }
    public void setCity(String city) { this.city = city; }
    public void setPostedDate(LocalDateTime postedDate) { this.postedDate = postedDate; }
    public void setSource(String source) { this.source = source; }
    public void setUrl(String url) { this.url = url; }
    public void setParsedDate(LocalDateTime parsedDate) { this.parsedDate = parsedDate; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public void setDescription(String description) { this.description = description; }
    public void setKeySkills(String keySkills) { this.keySkills = keySkills; }

    @Override
    public int compareTo(Vacancy other) {
        if (this.postedDate == null && other.postedDate == null) return 0;
        if (this.postedDate == null) return 1;
        if (other.postedDate == null) return -1;
        return this.postedDate.compareTo(other.postedDate);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vacancy vacancy = (Vacancy) o;
        return Objects.equals(id, vacancy.id) ||
                (externalId != null && externalId.equals(vacancy.externalId));
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, externalId);
    }

    @Override
    public String toString() {
        return String.format("Vacancy{id=%d, title='%s', company='%s', city='%s'}",
                id, title, company, city);
    }
}