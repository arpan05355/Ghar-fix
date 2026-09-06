package com.gharfix.service;

import com.gharfix.dto.ServiceDto;
import com.gharfix.entity.ServiceEntity;
import com.gharfix.repository.ServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ServiceCategoryService {

    private final ServiceRepository serviceRepository;

    public ServiceCategoryService(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    public List<ServiceEntity> getAllServices() {
        return serviceRepository.findAll();
    }

    public Optional<ServiceEntity> findByName(String name) {
        return serviceRepository.findByName(name);
    }

    public Optional<ServiceEntity> findByNameIgnoreCase(String name) {
        return serviceRepository.findByNameIgnoreCase(name);
    }

    public List<ServiceDto> getAllServiceDtos() {
        return serviceRepository.findAll().stream()
                .map(s -> new ServiceDto(s.getId(), s.getName(), s.getIcon(), s.getDescription(), s.getBasePricePerHour(), s.getEstimatedDurationMinutes()))
                .collect(Collectors.toList());
    }

    @Transactional
    public ServiceEntity save(ServiceEntity entity) {
        return serviceRepository.save(entity);
    }
}
