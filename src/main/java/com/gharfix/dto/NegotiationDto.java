package com.gharfix.dto;

import com.gharfix.entity.Booking;
import java.math.BigDecimal;

public class NegotiationDto {
    private Long id;
    private String serviceName;
    private Long workerId;
    private String workerName;
    private Long userId;
    private String userName;
    private BigDecimal proposedPrice;
    private String proposedBy;
    private String negotiationStatus;
    private String status;
    private String address;
    private String city;
    private String bookingDate;
    private String bookingTime;

    public NegotiationDto() {
    }

    public static NegotiationDto fromEntity(Booking b) {
        if (b == null) {
            return null;
        }
        NegotiationDto dto = new NegotiationDto();
        dto.setId(b.getId());
        dto.setServiceName(b.getServiceName());
        dto.setAddress(b.getAddress());
        dto.setCity(b.getCity());
        dto.setStatus(b.getStatus());
        dto.setProposedPrice(b.getProposedPrice());
        dto.setProposedBy(b.getProposedBy());
        dto.setNegotiationStatus(b.getNegotiationStatus());
        dto.setBookingDate(b.getBookingDate() != null ? b.getBookingDate().toString() : null);
        dto.setBookingTime(b.getBookingTime() != null ? b.getBookingTime().toString() : null);

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

    public BigDecimal getProposedPrice() {
        return proposedPrice;
    }

    public void setProposedPrice(BigDecimal proposedPrice) {
        this.proposedPrice = proposedPrice;
    }

    public String getProposedBy() {
        return proposedBy;
    }

    public void setProposedBy(String proposedBy) {
        this.proposedBy = proposedBy;
    }

    public String getNegotiationStatus() {
        return negotiationStatus;
    }

    public void setNegotiationStatus(String negotiationStatus) {
        this.negotiationStatus = negotiationStatus;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate(String bookingDate) {
        this.bookingDate = bookingDate;
    }

    public String getBookingTime() {
        return bookingTime;
    }

    public void setBookingTime(String bookingTime) {
        this.bookingTime = bookingTime;
    }
}
