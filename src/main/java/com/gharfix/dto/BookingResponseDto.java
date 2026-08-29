package com.gharfix.dto;

import com.gharfix.entity.Booking;

public class BookingResponseDto {
    private Long id;
    private String serviceName;
    private String address;
    private String city;
    private String date;
    private String time;
    private String status;
    private Long userId;
    private String userName;
    private Long workerId;
    private String workerName;

    public BookingResponseDto() {
    }

    public static BookingResponseDto fromEntity(Booking b) {
        if (b == null) {
            return null;
        }
        BookingResponseDto dto = new BookingResponseDto();
        dto.setId(b.getId());
        dto.setServiceName(b.getServiceName());
        dto.setAddress(b.getAddress());
        dto.setCity(b.getCity());
        dto.setDate(b.getBookingDate() != null ? b.getBookingDate().toString() : null);
        dto.setTime(b.getBookingTime() != null ? b.getBookingTime().toString() : null);
        dto.setStatus(b.getStatus());
        if (b.getUser() != null) {
            dto.setUserId(b.getUser().getId());
            dto.setUserName(b.getUser().getName());
        }
        if (b.getWorker() != null) {
            dto.setWorkerId(b.getWorker().getId());
            dto.setWorkerName(b.getWorker().getName());
        }
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Long getWorkerId() {
        return workerId;
    }

    public void setWorkerId(Long workerId) {
        this.workerId = workerId;
    }

    public String getWorkerName() {
        return workerName;
    }

    public void setWorkerName(String workerName) {
        this.workerName = workerName;
    }
}
