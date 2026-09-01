package com.gharfix.repository;

import com.gharfix.entity.ServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ServiceRepository extends JpaRepository<ServiceEntity, Long> {
    Optional<ServiceEntity> findByName(String name);
    Optional<ServiceEntity> findByNameIgnoreCase(String name);
}
