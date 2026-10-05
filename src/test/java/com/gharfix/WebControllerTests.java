package com.gharfix;

import com.gharfix.entity.Booking;
import com.gharfix.entity.User;
import com.gharfix.repository.BookingRepository;
import com.gharfix.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WebControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Test
    void testHomePage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("GharFix - Home Services at Your Doorstep")))
                .andExpect(content().string(containsString("Electrician")))
                .andExpect(content().string(containsString("Plumber")));
    }

    @Test
    void testUserRegistrationAndLogin() throws Exception {
        String testEmail = "testuser" + System.currentTimeMillis() + "@gharfix.com";

        // Register
        mockMvc.perform(post("/register")
                        .param("name", "Test User")
                        .param("email", testEmail)
                        .param("phone", "9876543210")
                        .param("password", "secret123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("flash_success", "Account created successfully! Please login."));

        // Login with correct credentials
        mockMvc.perform(post("/login")
                        .param("email", testEmail)
                        .param("password", "secret123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("flash_success", "Logged in successfully!"));

        // Login with invalid credentials
        mockMvc.perform(post("/login")
                        .param("email", testEmail)
                        .param("password", "wrongpass"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("flash_error", "Invalid email or password!"));
    }

    @Test
    void testMyBookingsProtectedForAnonymous() throws Exception {
        mockMvc.perform(get("/my-bookings"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @WithUserDetails(value = "demo@gharfix.com", userDetailsServiceBeanName = "customUserDetailsService")
    void testMyBookingsAuthenticatedWithBookings() throws Exception {
        User user = userRepository.findByEmail("demo@gharfix.com").orElse(null);
        if (user != null && bookingRepository.findByUserIdWithWorkerOrderByDateDesc(user.getId()).isEmpty()) {
            Booking booking = new Booking(user, null, "Plumber", "456 Test Street", "Vadodara",
                    LocalDate.now(), LocalTime.of(10, 0), "requested");
            bookingRepository.save(booking);
        }

        mockMvc.perform(get("/my-bookings"))
                .andExpect(status().isOk())
                .andExpect(view().name("bookings"))
                .andExpect(model().attributeExists("bookings", "currentUser"))
                .andExpect(content().string(containsString("My Bookings")))
                .andExpect(content().string(containsString("AI Help")));
    }

    @Test
    @WithUserDetails(value = "suresh@gharfix.com", userDetailsServiceBeanName = "workerUserDetailsService")
    void testHomePageAuthenticatedWithWorker() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("currentUser"))
                .andExpect(content().string(containsString("Suresh Patel")));
    }

    @Test
    @WithMockUser(username = "demo@gharfix.com", roles = {"USER"})
    void testLogout() throws Exception {
        mockMvc.perform(get("/logout"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(request().sessionAttribute("flash_success", "Logged out successfully!"));
    }

    @Test
    void testTermsOfService() throws Exception {
        mockMvc.perform(get("/terms-of-service"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Terms of Service")))
                .andExpect(content().string(containsString("1. Acceptance of Terms")))
                .andExpect(content().string(containsString("Effective Date: September 6, 2026")))
                .andExpect(content().string(containsString("/terms-of-service")))
                .andExpect(content().string(containsString("/privacy-policy")));
    }

    @Test
    void testPrivacyPolicy() throws Exception {
        mockMvc.perform(get("/privacy-policy"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Privacy Policy")))
                .andExpect(content().string(containsString("1. Introduction")))
                .andExpect(content().string(containsString("Effective Date: September 6, 2026")))
                .andExpect(content().string(containsString("/terms-of-service")))
                .andExpect(content().string(containsString("/privacy-policy")));
    }
}
