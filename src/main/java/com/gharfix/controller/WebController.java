package com.gharfix.controller;

import com.gharfix.dto.BookingRequestDto;
import com.gharfix.dto.NegotiationDto;
import com.gharfix.dto.RegisterRequestDto;
import com.gharfix.dto.WorkerRegisterDto;
import com.gharfix.entity.Booking;
import com.gharfix.entity.ServiceEntity;
import com.gharfix.entity.User;
import com.gharfix.entity.Worker;
import com.gharfix.entity.Review;
import com.gharfix.security.CustomUserDetails;
import com.gharfix.security.WorkerUserDetails;
import com.gharfix.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Controller
public class WebController {

    private final ServiceCategoryService serviceCategoryService;
    private final WorkerService workerService;
    private final ReviewService reviewService;
    private final UserService userService;
    private final BookingService bookingService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public WebController(ServiceCategoryService serviceCategoryService,
                         WorkerService workerService,
                         ReviewService reviewService,
                         UserService userService,
                         BookingService bookingService,
                         AuthenticationManager authenticationManager,
                         SecurityContextRepository securityContextRepository) {
        this.serviceCategoryService = serviceCategoryService;
        this.workerService = workerService;
        this.reviewService = reviewService;
        this.userService = userService;
        this.bookingService = bookingService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    @GetMapping("/")
    public String index(Model model,
                        @AuthenticationPrincipal Object principal,
                        HttpSession session) {
        List<ServiceEntity> services = serviceCategoryService.getAllServices();
        List<Worker> workers = workerService.getTop6Workers();
        List<Review> reviews = reviewService.getTop3Reviews();

        model.addAttribute("services", services);
        model.addAttribute("workers", workers);
        model.addAttribute("reviews", reviews);

        // Support both CustomUserDetails (User) and WorkerUserDetails (Worker) principals
        if (principal instanceof CustomUserDetails) {
            model.addAttribute("currentUser", principal);
        }

        // Check if session has a flash message from security handlers
        Object sessionFlashError = session.getAttribute("flash_error");
        if (sessionFlashError != null) {
            model.addAttribute("flash_error", sessionFlashError);
            session.removeAttribute("flash_error");
        }

        Object sessionFlashSuccess = session.getAttribute("flash_success");
        if (sessionFlashSuccess != null) {
            model.addAttribute("flash_success", sessionFlashSuccess);
            session.removeAttribute("flash_success");
        }

        return "index";
    }

    @GetMapping("/worker")
    public String workerLanding(Model model,
                                @AuthenticationPrincipal Object principal,
                                HttpSession session) {
        if (principal instanceof WorkerUserDetails) {
            return "redirect:/worker/dashboard";
        }
        List<ServiceEntity> services = serviceCategoryService.getAllServices();
        model.addAttribute("services", services);

        Object sessionFlashError = session.getAttribute("flash_error");
        if (sessionFlashError != null) {
            model.addAttribute("flash_error", sessionFlashError);
            session.removeAttribute("flash_error");
        }

        Object sessionFlashSuccess = session.getAttribute("flash_success");
        if (sessionFlashSuccess != null) {
            model.addAttribute("flash_success", sessionFlashSuccess);
            session.removeAttribute("flash_success");
        }

        return "worker_landing";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute RegisterRequestDto form,
                           RedirectAttributes redirectAttributes) {
        try {
            if (userService.existsByEmail(form.getEmail())) {
                redirectAttributes.addFlashAttribute("flash_error", "Email already registered!");
                return "redirect:/";
            }
            userService.registerUser(form);
            redirectAttributes.addFlashAttribute("flash_success", "Account created successfully! Please login.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("flash_error", "Registration failed: " + e.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/register/worker")
    public String registerWorker(@ModelAttribute WorkerRegisterDto form,
                                 RedirectAttributes redirectAttributes) {
        try {
            if (workerService.existsByEmail(form.getEmail())) {
                redirectAttributes.addFlashAttribute("flash_error", "Email already registered as a worker!");
                return "redirect:/worker";
            }
            workerService.registerWorker(form);
            redirectAttributes.addFlashAttribute("flash_success", "Worker account created! Please login.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("flash_error", "Registration failed: " + e.getMessage());
        }
        return "redirect:/worker";
    }

    @PostMapping("/login")
    public String login(@RequestParam("email") String email,
                        @RequestParam("password") String password,
                        HttpServletRequest request,
                        HttpServletResponse response,
                        RedirectAttributes redirectAttributes) {
        try {
            UsernamePasswordAuthenticationToken token =
                    new UsernamePasswordAuthenticationToken(email.trim(), password);
            Authentication authentication = authenticationManager.authenticate(token);

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);

            redirectAttributes.addFlashAttribute("flash_success", "Logged in successfully!");
        } catch (BadCredentialsException e) {
            redirectAttributes.addFlashAttribute("flash_error", "Invalid email or password!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("flash_error", "Login failed: " + e.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/worker/login")
    public String workerLogin(@RequestParam("email") String email,
                              @RequestParam("password") String password,
                              HttpServletRequest request,
                              HttpServletResponse response,
                              RedirectAttributes redirectAttributes) {
        try {
            UsernamePasswordAuthenticationToken token =
                    new UsernamePasswordAuthenticationToken(email.trim(), password);
            Authentication authentication = authenticationManager.authenticate(token);

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);

            redirectAttributes.addFlashAttribute("flash_success", "Logged in as worker!");
        } catch (BadCredentialsException e) {
            redirectAttributes.addFlashAttribute("flash_error", "Invalid email or password!");
            return "redirect:/worker";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("flash_error", "Login failed: " + e.getMessage());
            return "redirect:/worker";
        }
        return "redirect:/worker/dashboard";
    }

    @PostMapping("/book")
    public String book(@ModelAttribute BookingRequestDto form,
                       @RequestParam(value = "worker_id", required = false) Long workerIdParam,
                       @AuthenticationPrincipal CustomUserDetails userDetails,
                       RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            redirectAttributes.addFlashAttribute("flash_error", "Please login to book a service.");
            return "redirect:/";
        }

        if (form.getWorkerId() == null && workerIdParam != null) {
            form.setWorkerId(workerIdParam);
        }

        try {
            User user = userService.findById(userDetails.getId())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            bookingService.createBooking(user, form);
            redirectAttributes.addFlashAttribute("flash_success", "Booking confirmed! We will contact you soon.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("flash_error", "Booking failed: " + e.getMessage());
        }
        return "redirect:/";
    }

    @GetMapping("/my-bookings")
    public String myBookings(Model model,
                             @AuthenticationPrincipal CustomUserDetails userDetails,
                             RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            redirectAttributes.addFlashAttribute("flash_error", "Please login to view bookings.");
            return "redirect:/";
        }

        List<Booking> bookings = bookingService.getUserBookings(userDetails.getId());
        model.addAttribute("bookings", bookings);
        model.addAttribute("currentUser", userDetails);

        return "bookings";
    }

    @GetMapping({"/book", "/book/{serviceName}"})
    public String bookService(@PathVariable(value = "serviceName", required = false) String serviceName,
                              Model model,
                              @AuthenticationPrincipal Object principal,
                              HttpSession session) {
        List<ServiceEntity> services = serviceCategoryService.getAllServices();
        model.addAttribute("services", services);

        ServiceEntity selectedService = null;
        if (serviceName != null && !serviceName.isBlank()) {
            selectedService = serviceCategoryService.findByNameIgnoreCase(serviceName.trim())
                    .orElse(null);
        }
        if (selectedService == null && !services.isEmpty()) {
            selectedService = services.get(0);
        }

        model.addAttribute("selectedService", selectedService);
        if (selectedService != null) {
            model.addAttribute("serviceName", selectedService.getName());
            model.addAttribute("serviceIcon", selectedService.getIcon());
            model.addAttribute("basePricePerHour", selectedService.getBasePricePerHour());
            model.addAttribute("estimatedDurationMinutes", selectedService.getEstimatedDurationMinutes());

            int duration = selectedService.getEstimatedDurationMinutes() != null ? selectedService.getEstimatedDurationMinutes() : 60;
            int pricePerHour = selectedService.getBasePricePerHour() != null ? selectedService.getBasePricePerHour() : 150;
            int estimatedPrice = Math.round(((float) pricePerHour / 60.0f) * duration);
            model.addAttribute("estimatedPrice", estimatedPrice);
        }

        if (principal instanceof CustomUserDetails) {
            model.addAttribute("currentUser", principal);
        }

        Object sessionFlashError = session.getAttribute("flash_error");
        if (sessionFlashError != null) {
            model.addAttribute("flash_error", sessionFlashError);
            session.removeAttribute("flash_error");
        }

        Object sessionFlashSuccess = session.getAttribute("flash_success");
        if (sessionFlashSuccess != null) {
            model.addAttribute("flash_success", sessionFlashSuccess);
            session.removeAttribute("flash_success");
        }

        return "service-booking";
    }

    @GetMapping("/bookings/pending-negotiations")
    @ResponseBody
    public ResponseEntity<?> getPendingNegotiations(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.ok(List.of());
        }
        List<Booking> list = bookingService.getPendingNegotiationsForUser(userDetails.getId());
        List<NegotiationDto> dtos = list.stream().map(NegotiationDto::fromEntity).toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/bookings/{id}/accept-price")
    @ResponseBody
    public ResponseEntity<?> acceptPrice(@PathVariable Long id,
                                         @AuthenticationPrincipal CustomUserDetails userDetails,
                                         HttpServletRequest request) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Please login."));
        }
        try {
            Booking booking = bookingService.acceptBookingPriceByUser(id, userDetails.getId());
            return ResponseEntity.ok(Map.of("success", true, "message", "Price accepted successfully! Booking confirmed.", "booking", NegotiationDto.fromEntity(booking)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/bookings/{id}/counter-price")
    @ResponseBody
    public ResponseEntity<?> counterPrice(@PathVariable Long id,
                                          @RequestParam("price") BigDecimal price,
                                          @AuthenticationPrincipal CustomUserDetails userDetails,
                                          HttpServletRequest request) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Please login."));
        }
        try {
            Booking booking = bookingService.counterPriceByUser(id, userDetails.getId(), price);
            return ResponseEntity.ok(Map.of("success", true, "message", "Counter offer sent to professional!", "booking", NegotiationDto.fromEntity(booking)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
