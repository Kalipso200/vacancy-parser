package com.example.vacancyparser.repository;

import com.example.vacancyparser.model.Vacancy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VacancyJpaRepository extends
        CrudRepository<Vacancy, Long>,
        PagingAndSortingRepository<Vacancy, Long> {

    // Поиск по компании
    List<Vacancy> findByCompanyContainingIgnoreCase(String company);

    // Поиск по городу
    List<Vacancy> findByCityContainingIgnoreCase(String city);

    // Поиск по названию
    List<Vacancy> findByTitleContainingIgnoreCase(String title);

    // Поиск по источнику
    List<Vacancy> findBySource(String source);

    // Поиск по диапазону дат
    List<Vacancy> findByPostedDateBetween(LocalDateTime start, LocalDateTime end);

    // Поиск по внешнему ID
    Optional<Vacancy> findByExternalId(String externalId);

    // Поиск неархивированных
    List<Vacancy> findByArchivedFalse();

    // Сложный поиск
    @Query("SELECT v FROM Vacancy v WHERE " +
            "(:company IS NULL OR LOWER(v.company) LIKE LOWER(CONCAT('%', :company, '%'))) AND " +
            "(:city IS NULL OR LOWER(v.city) LIKE LOWER(CONCAT('%', :city, '%'))) AND " +
            "(:title IS NULL OR LOWER(v.title) LIKE LOWER(CONCAT('%', :title, '%')))")
    List<Vacancy> searchVacancies(@Param("company") String company,
                                  @Param("city") String city,
                                  @Param("title") String title);

    // Статистика по источникам
    @Query("SELECT v.source, COUNT(v) FROM Vacancy v GROUP BY v.source")
    List<Object[]> countBySource();

    // Статистика по городам
    @Query("SELECT v.city, COUNT(v) FROM Vacancy v GROUP BY v.city")
    List<Object[]> countByCity();

    // Статистика по дням (нативный запрос для H2)
    @Query(value = "SELECT DATE(v.posted_date), COUNT(*) FROM vacancies v GROUP BY DATE(v.posted_date) ORDER BY DATE(v.posted_date)", nativeQuery = true)
    List<Object[]> countByDayNative();

    // Последние вакансии с пагинацией
    Page<Vacancy> findAllByOrderByPostedDateDesc(Pageable pageable);

    // Удаление архивных
    @Modifying
    @Transactional
    @Query("DELETE FROM Vacancy v WHERE v.archived = true")
    void deleteAllArchived();

    // Поиск по ключевым навыкам
    @Query("SELECT v FROM Vacancy v WHERE LOWER(v.keySkills) LIKE LOWER(CONCAT('%', :skill, '%'))")
    List<Vacancy> findByKeySkill(@Param("skill") String skill);
}