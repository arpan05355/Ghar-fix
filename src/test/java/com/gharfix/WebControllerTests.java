package com.gharfix;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WebControllerTests {

    @Autowired
    private MockMvc mockMvc;

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
    @WithMockUser(username = "demo@gharfix.com", roles = {"USER"})
    void testLogout() throws Exception {
        mockMvc.perform(get("/logout"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(request().sessionAttribute("flash_success", "Logged out successfully!"));
    }
}
