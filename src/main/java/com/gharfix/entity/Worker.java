package com.gharfix.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "workers")
public class Worker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 150)
    private String service;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "worker_services", joinColumns = @JoinColumn(name = "worker_id"))
    @Column(name = "service_name", nullable = false, length = 50)
    private List<String> services = new ArrayList<>();

    @Column(nullable = false, length = 20)
    private String phone;

    @Column
    private Double rating = 4.0;

    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @Column(length = 50)
    private String experience;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(unique = true, length = 150)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "worker", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Booking> bookings = new ArrayList<>();

    @OneToMany(mappedBy = "worker", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Review> reviews = new ArrayList<>();

    public Worker() {
    }

    public Worker(String name, String service, String phone, Double rating, String imageUrl, String experience, String description) {
        this.name = name;
        this.service = service;
        if (service != null && !service.isBlank()) {
            this.services = new ArrayList<>(List.of(service));
        }
        this.phone = phone;
        this.rating = rating;
        this.imageUrl = imageUrl;
        this.experience = experience;
        this.description = description;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getService() {
        if (services != null && !services.isEmpty()) {
            return String.join(", ", services);
        }
        return service != null ? service : "";
    }

    public void setService(String service) {
        this.service = service;
        if (service != null && !service.isBlank() && (this.services == null || this.services.isEmpty())) {
            this.services = new ArrayList<>(List.of(service));
        }
    }

    public List<String> getServices() {
        return services;
    }

    public void setServices(List<String> services) {
        this.services = services != null ? new ArrayList<>(services) : new ArrayList<>();
        if (!this.services.isEmpty()) {
            this.service = String.join(", ", this.services);
        }
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getExperience() {
        return experience;
    }

    public void setExperience(String experience) {
        this.experience = experience;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<Booking> getBookings() {
        return bookings;
    }

    public void setBookings(List<Booking> bookings) {
        this.bookings = bookings;
    }

    public List<Review> getReviews() {
        return reviews;
    }

    public void setReviews(List<Review> reviews) {
        this.reviews = reviews;
    }
}
