package com.gharfix.controller;

import com.gharfix.entity.Booking;
import com.gharfix.entity.Worker;
import com.gharfix.security.WorkerUserDetails;
import com.gharfix.service.BookingService;
import com.gharfix.service.WorkerService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.gharfix.entity.ServiceEntity;
import com.gharfix.service.ServiceCategoryService;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/worker")
public class WorkerDashboardController {

    private final BookingService bookingService;
    private final WorkerService workerService;
    private final ServiceCategoryService serviceCategoryService;

    public WorkerDashboardController(BookingService bookingService,
                                     WorkerService workerService,
                                     ServiceCategoryService serviceCategoryService) {
        this.bookingService = bookingService;
        this.workerService = workerService;
        this.serviceCategoryService = serviceCategoryService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model,
                            @AuthenticationPrincipal WorkerUserDetails workerDetails) {
        if (workerDetails == null) {
            return "redirect:/worker";
        }

        Worker worker = workerService.findById(workerDetails.getId()).orElse(null);
        List<String> workerServices = (worker != null && worker.getServices() != null)
                ? worker.getServices()
                : (workerDetails.getServices() != null ? workerDetails.getServices() : List.of());

        // Available requests matching ANY of this worker's services
        List<Booking> availableRequests = bookingService.getRequestedBookingsForServices(workerServices);

        // This worker's accepted jobs
        List<Booking> acceptedJobs = bookingService.getAcceptedBookingsForWorker(workerDetails.getId());

        // All available platform services for the "My Services" selector
        List<ServiceEntity> allServices = serviceCategoryService.getAllServices();

        model.addAttribute("worker", worker);
        model.addAttribute("workerDetails", workerDetails);
        model.addAttribute("workerServices", workerServices);
        model.addAttribute("allServices", allServices);
        model.addAttribute("availableRequests", availableRequests);
        model.addAttribute("acceptedJobs", acceptedJobs);

        return "worker_dashboard";
    }

    @PostMapping("/dashboard/services/update")
    public String updateServices(@RequestParam(value = "services", required = false) List<String> services,
                                 @AuthenticationPrincipal WorkerUserDetails workerDetails,
                                 RedirectAttributes redirectAttributes) {
        if (workerDetails == null) {
            return "redirect:/worker";
        }

        if (services == null || services.isEmpty()) {
            redirectAttributes.addFlashAttribute("flash_error", "Please select at least one service category.");
            return "redirect:/worker/dashboard";
        }

        try {
            Worker worker = workerService.findById(workerDetails.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Worker not found"));
            worker.setServices(services);
            workerService.save(worker);
            redirectAttributes.addFlashAttribute("flash_success", "Your services have been updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("flash_error", "Failed to update services: " + e.getMessage());
        }

        return "redirect:/worker/dashboard";
    }

    @PostMapping("/dashboard/requests/{bookingId}/accept")
    public String acceptBooking(@PathVariable Long bookingId,
                                @AuthenticationPrincipal WorkerUserDetails workerDetails,
                                RedirectAttributes redirectAttributes) {
        if (workerDetails == null) {
            return "redirect:/";
        }

        try {
            Worker worker = workerService.findById(workerDetails.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Worker not found"));
            bookingService.acceptBooking(bookingId, worker);
            redirectAttributes.addFlashAttribute("flash_success", "Booking accepted successfully!");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("flash_error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("flash_error", "Failed to accept booking: " + e.getMessage());
        }

        return "redirect:/worker/dashboard";
    }

    @PostMapping("/dashboard/requests/{bookingId}/decline")
    public String declineBooking(@PathVariable Long bookingId,
                                 @AuthenticationPrincipal WorkerUserDetails workerDetails,
                                 RedirectAttributes redirectAttributes) {
        // Client-side decline via sessionStorage — no backend state change needed.
        // This endpoint exists for graceful handling if JS is disabled.
        redirectAttributes.addFlashAttribute("flash_success", "Request declined.");
        return "redirect:/worker/dashboard";
    }
}
