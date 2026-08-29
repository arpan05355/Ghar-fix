package com.gharfix.repository;

import com.gharfix.entity.Worker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkerRepository extends JpaRepository<Worker, Long> {
    List<Worker> findAllByOrderByRatingDesc();

    @Query("SELECT DISTINCT w FROM Worker w LEFT JOIN w.services s WHERE LOWER(s) = LOWER(:service) OR LOWER(w.service) LIKE LOWER(CONCAT('%', :service, '%')) ORDER BY w.rating DESC")
    List<Worker> findByServiceOrderByRatingDesc(@Param("service") String service);

    List<Worker> findTop6ByOrderByRatingDesc();

    Optional<Worker> findByName(String name);

    Optional<Worker> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT DISTINCT w FROM Worker w LEFT JOIN w.services s WHERE LOWER(w.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(s) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(w.service) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY w.rating DESC")
    List<Worker> searchByNameOrService(@Param("query") String query);
}
