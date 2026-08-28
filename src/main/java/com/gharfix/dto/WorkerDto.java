package com.gharfix.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class WorkerDto {
    private Long id;
    private String name;
    private String service;
    private Double rating;

    @JsonProperty("image_url")
    private String imageUrl;

    private String experience;

    public WorkerDto() {
    }

    public WorkerDto(Long id, String name, String service, Double rating, String imageUrl, String experience) {
        this.id = id;
        this.name = name;
        this.service = service;
        this.rating = rating;
        this.imageUrl = imageUrl;
        this.experience = experience;
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
        return service;
    }

    public void setService(String service) {
        this.service = service;
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
}
