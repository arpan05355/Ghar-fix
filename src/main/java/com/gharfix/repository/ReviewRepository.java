package com.gharfix.repository;

import com.gharfix.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Query("SELECT r FROM Review r JOIN FETCH r.worker JOIN FETCH r.user ORDER BY r.createdAt DESC")
    List<Review> findTop3ByOrderByCreatedAtDesc();

    boolean existsByComment(String comment);
}
