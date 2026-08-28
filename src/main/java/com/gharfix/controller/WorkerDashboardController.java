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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/worker")
public class WorkerDashboardController {

    private final BookingService bookingService;
    private final WorkerService workerService;

    public WorkerDashboardController(BookingService bookingService, WorkerService workerService) {
        this.bookingService = bookingService;
        this.workerService = workerService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model,
                            @AuthenticationPrincipal WorkerUserDetails workerDetails) {
        if (workerDetails == null) {
            return "redirect:/";
        }

        // Available requests matching this worker's service type
        List<Booking> availableRequests = bookingService.getRequestedBookingsForService(workerDetails.getService());

        // This worker's accepted jobs
        List<Booking> acceptedJobs = bookingService.getAcceptedBookingsForWorker(workerDetails.getId());

        model.addAttribute("workerDetails", workerDetails);
        model.addAttribute("availableRequests", availableRequests);
        model.addAttribute("acceptedJobs", acceptedJobs);

        return "worker_dashboard";
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
