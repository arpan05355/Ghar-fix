package com.gharfix.dto;

public class ServiceDto {
    private Long id;
    private String name;
    private String icon;
    private String description;
    private Integer basePricePerHour;
    private Integer estimatedDurationMinutes;

    public ServiceDto() {
    }

    public ServiceDto(Long id, String name, String icon, String description) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.description = description;
    }

    public ServiceDto(Long id, String name, String icon, String description, Integer basePricePerHour, Integer estimatedDurationMinutes) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.description = description;
        this.basePricePerHour = basePricePerHour;
        this.estimatedDurationMinutes = estimatedDurationMinutes;
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

    public Integer getBasePricePerHour() {
        return basePricePerHour;
    }

    public void setBasePricePerHour(Integer basePricePerHour) {
        this.basePricePerHour = basePricePerHour;
    }

    public Integer getEstimatedDurationMinutes() {
        return estimatedDurationMinutes;
    }

    public void setEstimatedDurationMinutes(Integer estimatedDurationMinutes) {
        this.estimatedDurationMinutes = estimatedDurationMinutes;
    }
}
