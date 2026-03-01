package com.example.vacancyparser.service.storage;

import com.example.vacancyparser.model.Vacancy;
import com.example.vacancyparser.repository.VacancyJpaRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@Transactional
public class VacancyJpaStorageService {

    private final VacancyJpaRepository repository;
    private final Executor parserExecutor;

    public VacancyJpaStorageService(VacancyJpaRepository repository,
                                    @Qualifier("parserExecutor") Executor parserExecutor) {
        this.repository = repository;
        this.parserExecutor = parserExecutor;
    }

    @Transactional
    public Vacancy save(Vacancy vacancy) {
        if (vacancy == null) {
            throw new IllegalArgumentException("Vacancy cannot be null");
        }
        if (vacancy.getExternalId() != null) {
            Optional<Vacancy> existing = repository.findByExternalId(vacancy.getExternalId());
            if (existing.isPresent()) {
                vacancy.setId(existing.get().getId());
            }
        }
        return repository.save(vacancy);
    }

    @Transactional
    public List<Vacancy> saveAll(List<Vacancy> vacancies) {
        if (vacancies == null) {
            return Collections.emptyList();
        }
        List<Vacancy> saved = new ArrayList<>();
        for (Vacancy vacancy : vacancies) {
            saved.add(save(vacancy));
        }
        return saved;
    }

    public CompletableFuture<List<Vacancy>> saveAllAsync(List<Vacancy> vacancies) {
        return CompletableFuture.supplyAsync(() -> saveAll(vacancies), parserExecutor);
    }

    @Transactional(readOnly = true)
    public List<Vacancy> getAllVacancies() {
        List<Vacancy> result = new ArrayList<>();
        repository.findAll().forEach(result::add);
        return result;
    }

    @Transactional(readOnly = true)
    public List<Vacancy> getVacanciesWithPagination(int page, int size, String sortBy, boolean ascending) {
        if (page < 0 || size <= 0) {
            throw new IllegalArgumentException("Page must be >= 0 and size must be > 0");
        }
        Sort sort = ascending ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return repository.findAll(pageable).getContent();
    }

    @Transactional(readOnly = true)
    public List<Vacancy> searchByCompany(String company) {
        return company != null && !company.trim().isEmpty()
                ? repository.findByCompanyContainingIgnoreCase(company.trim())
                : Collections.emptyList();
    }

    @Transactional(readOnly = true)
    public List<Vacancy> searchByCity(String city) {
        return city != null && !city.trim().isEmpty()
                ? repository.findByCityContainingIgnoreCase(city.trim())
                : Collections.emptyList();
    }

    @Transactional(readOnly = true)
    public List<Vacancy> searchByTitle(String title) {
        return title != null && !title.trim().isEmpty()
                ? repository.findByTitleContainingIgnoreCase(title.trim())
                : Collections.emptyList();
    }

    @Transactional(readOnly = true)
    public List<Vacancy> searchBySource(String source) {
        return source != null && !source.trim().isEmpty()
                ? repository.findBySource(source.trim())
                : Collections.emptyList();
    }

    @Transactional(readOnly = true)
    public List<Vacancy> searchByDateRange(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("From and to dates cannot be null");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("From date cannot be after to date");
        }
        return repository.findByPostedDateBetween(from, to);
    }

    @Transactional(readOnly = true)
    public List<Vacancy> advancedSearch(String company, String city, String title) {
        return repository.searchVacancies(company, city, title);
    }

    @Transactional(readOnly = true)
    public List<Vacancy> searchByKeySkill(String skill) {
        return skill != null && !skill.trim().isEmpty()
                ? repository.findByKeySkill(skill.trim())
                : Collections.emptyList();
    }

    @Transactional(readOnly = true)
    public Vacancy findByExternalId(String externalId) {
        return externalId != null
                ? repository.findByExternalId(externalId).orElse(null)
                : null;
    }

    @Transactional
    public void clearAll() {
        repository.deleteAll();
    }

    @Transactional(readOnly = true)
    public long getTotalCount() {
        return repository.count();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getStatsBySource() {
        Map<String, Long> stats = new HashMap<>();
        repository.countBySource().forEach(obj ->
                stats.put((String) obj[0], (Long) obj[1])
        );
        return stats;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getStatsByCity() {
        Map<String, Long> stats = new HashMap<>();
        repository.countByCity().forEach(obj ->
                stats.put((String) obj[0], (Long) obj[1])
        );
        return stats;
    }

    @Transactional(readOnly = true)
    public Map<LocalDate, Long> getStatsByDay() {
        Map<LocalDate, Long> stats = new LinkedHashMap<>();

        List<Object[]> results = repository.countByDayNative();

        for (Object[] result : results) {
            if (result[0] instanceof java.sql.Date) {
                java.sql.Date sqlDate = (java.sql.Date) result[0];
                LocalDate date = sqlDate.toLocalDate();
                Long count = (Long) result[1];
                stats.put(date, count);
            }
        }

        return stats;
    }

    @Transactional(readOnly = true)
    public Vacancy findById(Long id) {
        return id != null ? repository.findById(id).orElse(null) : null;
    }

    @Transactional
    public void deleteById(Long id) {
        if (id != null) {
            repository.deleteById(id);
        }
    }

    @Transactional
    public void deleteAllArchived() {
        repository.deleteAllArchived();
    }
}