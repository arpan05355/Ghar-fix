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

    List<Worker> findByServiceOrderByRatingDesc(String service);

    List<Worker> findTop6ByOrderByRatingDesc();

    Optional<Worker> findByName(String name);

    Optional<Worker> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT w FROM Worker w WHERE LOWER(w.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(w.service) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY w.rating DESC")
    List<Worker> searchByNameOrService(@Param("query") String query);
}
