package com.gharfix.service;

import com.gharfix.entity.Review;
import com.gharfix.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;

    public ReviewService(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    public List<Review> getTop3Reviews() {
        return reviewRepository.findTop3ByOrderByCreatedAtDesc();
    }

    public boolean existsByComment(String comment) {
        return reviewRepository.existsByComment(comment);
    }

    @Transactional
    public Review save(Review review) {
        return reviewRepository.save(review);
    }
}
