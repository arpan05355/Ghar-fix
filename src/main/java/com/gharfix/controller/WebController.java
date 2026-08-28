package com.gharfix.controller;

import com.gharfix.dto.BookingRequestDto;
import com.gharfix.dto.RegisterRequestDto;
import com.gharfix.dto.WorkerRegisterDto;
import com.gharfix.entity.Booking;
import com.gharfix.entity.ServiceEntity;
import com.gharfix.entity.User;
import com.gharfix.entity.Worker;
import com.gharfix.entity.Review;
import com.gharfix.security.CustomUserDetails;
import com.gharfix.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

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
                return "redirect:/";
            }
            workerService.registerWorker(form);
            redirectAttributes.addFlashAttribute("flash_success", "Worker account created! Please login.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("flash_error", "Registration failed: " + e.getMessage());
        }
        return "redirect:/";
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
            return "redirect:/";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("flash_error", "Login failed: " + e.getMessage());
            return "redirect:/";
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
}
