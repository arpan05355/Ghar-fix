package com.gharfix.controller;

import com.gharfix.dto.ServiceDto;
import com.gharfix.dto.WorkerDto;
import com.gharfix.dto.WorkerSearchDto;
import com.gharfix.service.ServiceCategoryService;
import com.gharfix.service.WorkerService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final ServiceCategoryService serviceCategoryService;
    private final WorkerService workerService;

    public ApiController(ServiceCategoryService serviceCategoryService, WorkerService workerService) {
        this.serviceCategoryService = serviceCategoryService;
        this.workerService = workerService;
    }

    @GetMapping("/services")
    public List<ServiceDto> getServices() {
        return serviceCategoryService.getAllServiceDtos();
    }

    @GetMapping("/workers")
    public List<WorkerDto> getWorkers(@RequestParam(value = "service", required = false) String service) {
        return workerService.getWorkerDtos(service);
    }

    @GetMapping("/search")
    public List<WorkerSearchDto> searchWorkers(@RequestParam(value = "q", defaultValue = "") String query) {
        return workerService.getSearchDtos(query);
    }
}
