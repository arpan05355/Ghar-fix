package com.gharfix.service;

import com.gharfix.dto.WorkerDto;
import com.gharfix.dto.WorkerRegisterDto;
import com.gharfix.dto.WorkerSearchDto;
import com.gharfix.entity.Worker;
import com.gharfix.repository.WorkerRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@SuppressWarnings("null")
public class WorkerService {

    private final WorkerRepository workerRepository;
    private final PasswordEncoder passwordEncoder;

    public WorkerService(WorkerRepository workerRepository, PasswordEncoder passwordEncoder) {
        this.workerRepository = workerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Worker> getAllWorkers() {
        return workerRepository.findAllByOrderByRatingDesc();
    }

    public List<Worker> getTop6Workers() {
        return workerRepository.findTop6ByOrderByRatingDesc();
    }

    public List<Worker> getWorkersByService(String service) {
        if (service != null && !service.isBlank()) {
            return workerRepository.findByServiceOrderByRatingDesc(service);
        }
        return workerRepository.findAllByOrderByRatingDesc();
    }

    public List<Worker> searchWorkers(String query) {
        if (query == null || query.isBlank()) {
            return workerRepository.findAllByOrderByRatingDesc();
        }
        return workerRepository.searchByNameOrService(query.trim());
    }

    public Optional<Worker> findById(Long id) {
        return workerRepository.findById(id);
    }

    public Optional<Worker> findByEmail(String email) {
        return workerRepository.findByEmail(email);
    }

    public boolean existsByEmail(String email) {
        return workerRepository.existsByEmail(email);
    }

    public List<WorkerDto> getWorkerDtos(String service) {
        List<Worker> workers = getWorkersByService(service);
        return workers.stream()
                .map(w -> new WorkerDto(w.getId(), w.getName(), w.getService(), w.getRating(), w.getImageUrl(), w.getExperience()))
                .collect(Collectors.toList());
    }

    public List<WorkerSearchDto> getSearchDtos(String query) {
        List<Worker> workers = searchWorkers(query);
        return workers.stream()
                .map(w -> new WorkerSearchDto(w.getId(), w.getName(), w.getService(), w.getRating()))
                .collect(Collectors.toList());
    }

    @Transactional
    public Worker registerWorker(WorkerRegisterDto request) {
        if (workerRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered as a worker!");
        }

        Worker worker = new Worker();
        worker.setName(request.getName());
        if (request.getServices() != null && !request.getServices().isEmpty()) {
            worker.setServices(new ArrayList<>(request.getServices()));
        } else if (request.getService() != null && !request.getService().isBlank()) {
            worker.setService(request.getService());
        }
        worker.setPhone(request.getPhone());
        worker.setEmail(request.getEmail());
        worker.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        worker.setRating(4.0);

        return workerRepository.save(worker);
    }

    @Transactional
    public Worker save(Worker worker) {
        return workerRepository.save(worker);
    }
}
