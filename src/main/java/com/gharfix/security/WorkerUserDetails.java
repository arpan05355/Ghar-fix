package com.gharfix.security;

import com.gharfix.entity.Worker;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class WorkerUserDetails implements UserDetails {

    private final Long id;
    private final String name;
    private final String email;
    private final String service;
    private final List<String> services;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;

    public WorkerUserDetails(Worker worker) {
        this.id = worker.getId();
        this.name = worker.getName();
        this.email = worker.getEmail();
        this.services = worker.getServices() != null ? new ArrayList<>(worker.getServices()) : new ArrayList<>();
        this.service = worker.getService() != null ? worker.getService() : "";
        this.password = worker.getPasswordHash();
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_WORKER"));
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getService() {
        return service;
    }

    public List<String> getServices() {
        return services;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
