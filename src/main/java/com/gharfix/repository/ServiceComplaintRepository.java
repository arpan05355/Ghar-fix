package com.gharfix.repository;

import com.gharfix.entity.ServiceComplaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceComplaintRepository extends JpaRepository<ServiceComplaint, Long> {

    List<ServiceComplaint> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ServiceComplaint> findByBookingId(Long bookingId);

    Optional<ServiceComplaint> findByTicketNumber(String ticketNumber);
}
