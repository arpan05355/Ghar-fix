package com.gharfix.dto;

import java.util.ArrayList;
import java.util.List;

public class WorkerRegisterDto {
    private String name;
    private String service;
    private List<String> services = new ArrayList<>();
    private String phone;
    private String email;
    private String password;

    public WorkerRegisterDto() {
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

    public List<String> getServices() {
        if ((services == null || services.isEmpty()) && service != null && !service.isBlank()) {
            List<String> list = new ArrayList<>();
            list.add(service);
            return list;
        }
        return services != null ? services : new ArrayList<>();
    }

    public void setServices(List<String> services) {
        this.services = services != null ? services : new ArrayList<>();
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
