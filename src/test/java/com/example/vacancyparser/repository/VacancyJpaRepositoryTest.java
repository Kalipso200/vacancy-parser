package com.example.vacancyparser.repository;

import com.example.vacancyparser.model.Vacancy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
public class VacancyJpaRepositoryTest {

    @Autowired
    private VacancyJpaRepository repository;

    private Vacancy testVacancy1;
    private Vacancy testVacancy2;
    private Vacancy testVacancy3;

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        testVacancy1 = new Vacancy();
        testVacancy1.setTitle("Java Developer");
        testVacancy1.setCompany("Яндекс");
        testVacancy1.setCity("Москва");
        testVacancy1.setSource("hh.ru");
        testVacancy1.setExternalId("ext123");
        testVacancy1.setSalary("от 250000 ₽");
        testVacancy1.setRequirements("Java, Spring, Hibernate");
        testVacancy1.setKeySkills("Java, Spring, Hibernate");
        testVacancy1.setPostedDate(LocalDateTime.now().minusDays(5));
        testVacancy1.setArchived(false);

        testVacancy2 = new Vacancy();
        testVacancy2.setTitle("Python Developer");
        testVacancy2.setCompany("Тинькофф");
        testVacancy2.setCity("Санкт-Петербург");
        testVacancy2.setSource("hh.ru");
        testVacancy2.setExternalId("ext456");
        testVacancy2.setSalary("от 200000 ₽");
        testVacancy2.setRequirements("Python, Django, FastAPI");
        testVacancy2.setKeySkills("Python, Django");
        testVacancy2.setPostedDate(LocalDateTime.now().minusDays(3));
        testVacancy2.setArchived(false);

        testVacancy3 = new Vacancy();
        testVacancy3.setTitle("DevOps Engineer");
        testVacancy3.setCompany("Яндекс");
        testVacancy3.setCity("Москва");
        testVacancy3.setSource("superjob.ru");
        testVacancy3.setExternalId("ext789");
        testVacancy3.setSalary("от 300000 ₽");
        testVacancy3.setRequirements("Kubernetes, Docker, CI/CD");
        testVacancy3.setKeySkills("Kubernetes, Docker");
        testVacancy3.setPostedDate(LocalDateTime.now().minusDays(1));
        testVacancy3.setArchived(true);

        repository.save(testVacancy1);
        repository.save(testVacancy2);
        repository.save(testVacancy3);
    }

    @Test
    void testFindByCompanyContainingIgnoreCase() {
        List<Vacancy> result = repository.findByCompanyContainingIgnoreCase("яндекс");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Vacancy::getCompany)
                .allMatch(company -> company.toLowerCase().contains("яндекс"));
    }

    @Test
    void testFindByCityContainingIgnoreCase() {
        List<Vacancy> result = repository.findByCityContainingIgnoreCase("москва");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Vacancy::getCity)
                .allMatch(city -> city.toLowerCase().contains("москва"));
    }

    @Test
    void testFindByTitleContainingIgnoreCase() {
        List<Vacancy> result = repository.findByTitleContainingIgnoreCase("java");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Java Developer");
    }

    @Test
    void testFindBySource() {
        List<Vacancy> result = repository.findBySource("hh.ru");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Vacancy::getSource)
                .allMatch(source -> source.equals("hh.ru"));
    }

    @Test
    void testFindByExternalId() {
        Optional<Vacancy> result = repository.findByExternalId("ext123");

        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("Java Developer");
    }

    @Test
    void testFindByExternalIdNotFound() {
        Optional<Vacancy> result = repository.findByExternalId("nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    void testFindByArchivedFalse() {
        List<Vacancy> result = repository.findByArchivedFalse();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Vacancy::isArchived)
                .allMatch(archived -> !archived);
    }

    @Test
    void testFindByPostedDateBetween() {
        LocalDateTime from = LocalDateTime.now().minusDays(4);
        LocalDateTime to = LocalDateTime.now();

        List<Vacancy> result = repository.findByPostedDateBetween(from, to);

        assertThat(result).hasSize(2);
    }

    @Test
    void testSearchVacancies() {
        List<Vacancy> result = repository.searchVacancies("яндекс", "москва", null);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Vacancy::getCompany)
                .allMatch(company -> company.toLowerCase().contains("яндекс"));
        assertThat(result).extracting(Vacancy::getCity)
                .allMatch(city -> city.toLowerCase().contains("москва"));
    }

    @Test
    void testCountBySource() {
        List<Object[]> result = repository.countBySource();

        assertThat(result).hasSize(2);

        Map<String, Long> counts = new HashMap<>();
        result.forEach(arr -> counts.put((String) arr[0], (Long) arr[1]));

        assertThat(counts).containsEntry("hh.ru", 2L);
        assertThat(counts).containsEntry("superjob.ru", 1L);
    }

    @Test
    void testCountByCity() {
        List<Object[]> result = repository.countByCity();

        assertThat(result).hasSize(2);

        Map<String, Long> counts = new HashMap<>();
        result.forEach(arr -> counts.put((String) arr[0], (Long) arr[1]));

        assertThat(counts).containsEntry("Москва", 2L);
        assertThat(counts).containsEntry("Санкт-Петербург", 1L);
    }

    @Test
    void testCountByDayNative() {
        List<Object[]> result = repository.countByDayNative();
        assertThat(result).isNotNull();
    }

    @Test
    void testFindByKeySkill() {
        List<Vacancy> result = repository.findByKeySkill("java");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Java Developer");
    }

    @Test
    void testFindAllByOrderByPostedDateDesc() {
        PageRequest pageRequest = PageRequest.of(0, 2, Sort.by("postedDate").descending());
        Page<Vacancy> page = repository.findAllByOrderByPostedDateDesc(pageRequest);

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(3);

        List<Vacancy> vacancies = page.getContent();
        assertThat(vacancies.get(0).getTitle()).isEqualTo("DevOps Engineer");
    }

    @Test
    void testDeleteAllArchived() {
        long beforeCount = repository.count();
        assertThat(beforeCount).isEqualTo(3);

        repository.deleteAllArchived();

        long afterCount = repository.count();
        assertThat(afterCount).isEqualTo(2);

        List<Vacancy> remaining = repository.findAll();
        assertThat(remaining).extracting(Vacancy::isArchived)
                .allMatch(archived -> !archived);
    }
}