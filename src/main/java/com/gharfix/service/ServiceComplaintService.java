package com.gharfix.service;

import com.gharfix.entity.Booking;
import com.gharfix.entity.ServiceComplaint;
import com.gharfix.entity.User;
import com.gharfix.repository.ServiceComplaintRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ServiceComplaintService {

    private static final Logger log = LoggerFactory.getLogger(ServiceComplaintService.class);

    private final ServiceComplaintRepository complaintRepository;

    public ServiceComplaintService(ServiceComplaintRepository complaintRepository) {
        this.complaintRepository = complaintRepository;
    }

    @Transactional
    public ServiceComplaint registerComplaint(User user, Booking booking, String serviceCategory,
                                              String description, String severity) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null when registering a complaint.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Complaint description cannot be empty.");
        }

        String category = (serviceCategory != null && !serviceCategory.isBlank())
                ? serviceCategory
                : (booking != null ? booking.getServiceName() : "General Home Service");

        String ticketNumber = "GF-CMP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        ServiceComplaint complaint = new ServiceComplaint(
                user,
                booking,
                category,
                description.trim(),
                severity != null ? severity.toUpperCase() : "MEDIUM",
                ticketNumber
        );

        ServiceComplaint saved = complaintRepository.save(complaint);
        log.info("Registered new service complaint ticket={} for userId={}, category={}",
                ticketNumber, user.getId(), category);
        return saved;
    }

    public List<ServiceComplaint> getUserComplaints(Long userId) {
        return complaintRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Optional<ServiceComplaint> findByTicketNumber(String ticketNumber) {
        return complaintRepository.findByTicketNumber(ticketNumber);
    }
}
