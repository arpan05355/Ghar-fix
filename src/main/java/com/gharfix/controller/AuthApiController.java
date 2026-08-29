package com.gharfix.controller;

import com.gharfix.dto.LoginRequestDto;
import com.gharfix.dto.RegisterRequestDto;
import com.gharfix.dto.WorkerRegisterDto;
import com.gharfix.security.CustomUserDetails;
import com.gharfix.security.WorkerUserDetails;
import com.gharfix.security.jwt.JwtUtil;
import com.gharfix.service.UserService;
import com.gharfix.service.WorkerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    private final UserService userService;
    private final WorkerService workerService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    public AuthApiController(UserService userService,
                             WorkerService workerService,
                             AuthenticationManager authenticationManager,
                             JwtUtil jwtUtil) {
        this.userService = userService;
        this.workerService = workerService;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequestDto form) {
        Map<String, Object> response = new LinkedHashMap<>();
        if (form.getEmail() == null || form.getEmail().isBlank()) {
            response.put("success", false);
            response.put("message", "Email is required");
            return ResponseEntity.badRequest().body(response);
        }

        if (userService.existsByEmail(form.getEmail().trim())) {
            response.put("success", false);
            response.put("message", "Email already registered!");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        try {
            userService.registerUser(form);
            response.put("success", true);
            response.put("message", "Account created successfully! Please login.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Registration failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/register/worker")
    public ResponseEntity<Map<String, Object>> registerWorker(@RequestBody WorkerRegisterDto form) {
        Map<String, Object> response = new LinkedHashMap<>();
        if (form.getEmail() == null || form.getEmail().isBlank()) {
            response.put("success", false);
            response.put("message", "Email is required");
            return ResponseEntity.badRequest().body(response);
        }

        if (workerService.existsByEmail(form.getEmail().trim())) {
            response.put("success", false);
            response.put("message", "Email already registered as a worker!");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        try {
            workerService.registerWorker(form);
            response.put("success", true);
            response.put("message", "Worker account created! Please login.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Registration failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequestDto form) {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            UsernamePasswordAuthenticationToken token =
                    new UsernamePasswordAuthenticationToken(form.getEmail().trim(), form.getPassword());
            Authentication authentication = authenticationManager.authenticate(token);

            if (!(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
                response.put("success", false);
                response.put("message", "Invalid credentials for regular user.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            String jwt = jwtUtil.generateToken(userDetails.getUsername(), "ROLE_USER", userDetails.getId(), userDetails.getName());

            response.put("token", jwt);
            response.put("role", "USER");
            response.put("id", userDetails.getId());
            response.put("name", userDetails.getName());
            response.put("email", userDetails.getUsername());
            return ResponseEntity.ok(response);
        } catch (BadCredentialsException e) {
            response.put("success", false);
            response.put("message", "Invalid email or password!");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Login failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/worker/login")
    public ResponseEntity<Map<String, Object>> workerLogin(@RequestBody LoginRequestDto form) {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            UsernamePasswordAuthenticationToken token =
                    new UsernamePasswordAuthenticationToken(form.getEmail().trim(), form.getPassword());
            Authentication authentication = authenticationManager.authenticate(token);

            if (!(authentication.getPrincipal() instanceof WorkerUserDetails workerDetails)) {
                response.put("success", false);
                response.put("message", "Invalid credentials for worker.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            String jwt = jwtUtil.generateToken(workerDetails.getUsername(), "ROLE_WORKER", workerDetails.getId(), workerDetails.getName());

            response.put("token", jwt);
            response.put("role", "WORKER");
            response.put("id", workerDetails.getId());
            response.put("name", workerDetails.getName());
            response.put("email", workerDetails.getUsername());
            return ResponseEntity.ok(response);
        } catch (BadCredentialsException e) {
            response.put("success", false);
            response.put("message", "Invalid email or password!");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Login failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
