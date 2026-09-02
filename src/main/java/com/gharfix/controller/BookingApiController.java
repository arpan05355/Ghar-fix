package com.gharfix.controller;

import com.gharfix.dto.BookingRequestDto;
import com.gharfix.dto.BookingResponseDto;
import com.gharfix.entity.Booking;
import com.gharfix.entity.User;
import com.gharfix.entity.Worker;
import com.gharfix.security.CustomUserDetails;
import com.gharfix.security.WorkerUserDetails;
import com.gharfix.service.BookingService;
import com.gharfix.service.UserService;
import com.gharfix.service.WorkerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class BookingApiController {

    private final BookingService bookingService;
    private final UserService userService;
    private final WorkerService workerService;

    public BookingApiController(BookingService bookingService,
                                UserService userService,
                                WorkerService workerService) {
        this.bookingService = bookingService;
        this.userService = userService;
        this.workerService = workerService;
    }

    @GetMapping("/bookings")
    public ResponseEntity<?> getUserBookings(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "User not authenticated"));
        }

        List<Booking> bookings = bookingService.getUserBookings(userDetails.getId());
        List<BookingResponseDto> result = bookings.stream()
                .map(BookingResponseDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/bookings")
    public ResponseEntity<?> createBooking(@RequestBody BookingRequestDto form,
                                           @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "User not authenticated"));
        }

        try {
            User user = userService.findById(userDetails.getId())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            Booking booking = bookingService.createBooking(user, form);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Booking created successfully");
            response.put("booking", BookingResponseDto.fromEntity(booking));
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to create booking: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/worker/requests")
    public ResponseEntity<?> getWorkerRequests(@AuthenticationPrincipal WorkerUserDetails workerDetails) {
        if (workerDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Worker not authenticated"));
        }

        Worker worker = workerService.findById(workerDetails.getId()).orElse(null);
        List<String> services = (worker != null && worker.getServices() != null)
                ? worker.getServices()
                : workerDetails.getServices();

        List<Booking> requests = bookingService.getRequestedBookingsForServices(services, workerDetails.getId());
        List<BookingResponseDto> result = requests.stream()
                .map(BookingResponseDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/worker/requests/{id}/accept")
    public ResponseEntity<?> acceptBooking(@PathVariable("id") Long id,
                                          @AuthenticationPrincipal WorkerUserDetails workerDetails) {
        if (workerDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Worker not authenticated"));
        }

        try {
            Worker worker = workerService.findById(workerDetails.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Worker not found"));

            Booking accepted = bookingService.acceptBooking(id, worker);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Booking accepted successfully");
            response.put("booking", BookingResponseDto.fromEntity(accepted));
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Error accepting booking: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/worker/requests/{id}/decline")
    public ResponseEntity<?> declineBooking(@PathVariable("id") Long id,
                                           @AuthenticationPrincipal WorkerUserDetails workerDetails) {
        if (workerDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Worker not authenticated"));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Booking request declined",
                "bookingId", id
        ));
    }
}
