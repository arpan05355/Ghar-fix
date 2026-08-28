package com.gharfix.dto;

public class WorkerSearchDto {
    private Long id;
    private String name;
    private String service;
    private Double rating;

    public WorkerSearchDto() {
    }

    public WorkerSearchDto(Long id, String name, String service, Double rating) {
        this.id = id;
        this.name = name;
        this.service = service;
        this.rating = rating;
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
}
