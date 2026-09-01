package com.gharfix.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "services")
public class ServiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 50)
    private String icon;

    @Column(length = 200)
    private String description;

    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes = 60;

    @Column(name = "base_price_per_hour")
    private Integer basePricePerHour = 150;

    public ServiceEntity() {
    }

    public ServiceEntity(String name, String icon, String description) {
        this.name = name;
        this.icon = icon;
        this.description = description;
    }

    public ServiceEntity(String name, String icon, String description, Integer estimatedDurationMinutes, Integer basePricePerHour) {
        this.name = name;
        this.icon = icon;
        this.description = description;
        this.estimatedDurationMinutes = estimatedDurationMinutes;
        this.basePricePerHour = basePricePerHour;
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

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getEstimatedDurationMinutes() {
        return estimatedDurationMinutes;
    }

    public void setEstimatedDurationMinutes(Integer estimatedDurationMinutes) {
        this.estimatedDurationMinutes = estimatedDurationMinutes;
    }

    public Integer getBasePricePerHour() {
        return basePricePerHour;
    }

    public void setBasePricePerHour(Integer basePricePerHour) {
        this.basePricePerHour = basePricePerHour;
    }
}
