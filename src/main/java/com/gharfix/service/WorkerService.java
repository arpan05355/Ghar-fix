package com.gharfix.service;

import com.gharfix.dto.WorkerDto;
import com.gharfix.dto.WorkerSearchDto;
import com.gharfix.entity.Worker;
import com.gharfix.repository.WorkerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class WorkerService {

    private final WorkerRepository workerRepository;

    public WorkerService(WorkerRepository workerRepository) {
        this.workerRepository = workerRepository;
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
    public Worker save(Worker worker) {
        return workerRepository.save(worker);
    }
}
