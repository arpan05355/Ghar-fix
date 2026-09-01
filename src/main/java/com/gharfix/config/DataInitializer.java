package com.gharfix.config;

import com.gharfix.entity.*;
import com.gharfix.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.File;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final ServiceRepository serviceRepository;
    private final WorkerRepository workerRepository;
    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(ServiceRepository serviceRepository,
                           WorkerRepository workerRepository,
                           UserRepository userRepository,
                           ReviewRepository reviewRepository,
                           BookingRepository bookingRepository,
                           PasswordEncoder passwordEncoder) {
        this.serviceRepository = serviceRepository;
        this.workerRepository = workerRepository;
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
        this.bookingRepository = bookingRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedServices();
        seedWorkers();
        seedUsersAndReviews();
        importFromSqliteIfPresent();
    }

    private void seedServices() {
        if (serviceRepository.count() == 0) {
            List<ServiceEntity> services = List.of(
                    new ServiceEntity("Electrician", "fa-bolt", "Electrical repairs and installations", 90, 150),
                    new ServiceEntity("Plumber", "fa-wrench", "Pipe fitting and water repairs", 60, 180),
                    new ServiceEntity("Carpenter", "fa-hammer", "Furniture and woodwork", 120, 160),
                    new ServiceEntity("AC Service", "fa-snowflake", "Air conditioner repair and service", 90, 200),
                    new ServiceEntity("House Maid", "fa-broom", "Home cleaning services", 120, 100),
                    new ServiceEntity("Laundry", "fa-shirt", "Cloth washing and ironing", 45, 80),
                    new ServiceEntity("Labour", "fa-person-digging", "General labor work", 180, 120),
                    new ServiceEntity("Contractor", "fa-building", "Construction and renovation", 240, 250)
            );
            serviceRepository.saveAll(services);
            log.info("Default services seeded successfully ({} items).", services.size());
        } else {
            // Update existing services if price or duration is missing/zero
            List<ServiceEntity> existing = serviceRepository.findAll();
            for (ServiceEntity s : existing) {
                boolean updated = false;
                if (s.getBasePricePerHour() == null || s.getBasePricePerHour() == 0 ||
                    s.getEstimatedDurationMinutes() == null || s.getEstimatedDurationMinutes() == 0) {
                    switch (s.getName()) {
                        case "Electrician" -> { s.setEstimatedDurationMinutes(90); s.setBasePricePerHour(150); }
                        case "Plumber" -> { s.setEstimatedDurationMinutes(60); s.setBasePricePerHour(180); }
                        case "Carpenter" -> { s.setEstimatedDurationMinutes(120); s.setBasePricePerHour(160); }
                        case "AC Service" -> { s.setEstimatedDurationMinutes(90); s.setBasePricePerHour(200); }
                        case "House Maid" -> { s.setEstimatedDurationMinutes(120); s.setBasePricePerHour(100); }
                        case "Laundry" -> { s.setEstimatedDurationMinutes(45); s.setBasePricePerHour(80); }
                        case "Labour" -> { s.setEstimatedDurationMinutes(180); s.setBasePricePerHour(120); }
                        case "Contractor" -> { s.setEstimatedDurationMinutes(240); s.setBasePricePerHour(250); }
                        default -> { s.setEstimatedDurationMinutes(60); s.setBasePricePerHour(150); }
                    }
                    updated = true;
                }
                if (updated) {
                    serviceRepository.save(s);
                    log.info("Updated service {} with duration {} min, rate ₹{}/hr", s.getName(), s.getEstimatedDurationMinutes(), s.getBasePricePerHour());
                }
            }
        }
    }

    private void seedWorkers() {
        if (workerRepository.count() == 0) {
            String hashedPassword = passwordEncoder.encode("worker123");

            Worker w1 = new Worker("Ramesh Kumar", "Plumber", "9876543210", 4.5, "https://randomuser.me/api/portraits/men/32.jpg", "5 years", "Expert in pipe fitting and leak repairs");
            w1.setEmail("ramesh@gharfix.com");
            w1.setPasswordHash(hashedPassword);
            w1.setServices(new java.util.ArrayList<>(List.of("Plumber")));

            Worker w2 = new Worker("Suresh Patel", "Electrician", "9876543211", 4.7, "https://randomuser.me/api/portraits/men/45.jpg", "7 years", "Specialist in wiring and electrical installations");
            w2.setEmail("suresh@gharfix.com");
            w2.setPasswordHash(hashedPassword);
            w2.setServices(new java.util.ArrayList<>(List.of("Electrician")));

            Worker w3 = new Worker("Amit Sharma", "Carpenter", "9876543212", 4.6, "https://randomuser.me/api/portraits/men/60.jpg", "8 years", "Expert furniture maker and repairs");
            w3.setEmail("amit@gharfix.com");
            w3.setPasswordHash(hashedPassword);
            w3.setServices(new java.util.ArrayList<>(List.of("Carpenter")));

            Worker w4 = new Worker("Raj Malhotra", "AC Service", "9876543213", 4.8, "https://randomuser.me/api/portraits/men/22.jpg", "6 years", "AC repair and maintenance expert");
            w4.setEmail("raj@gharfix.com");
            w4.setPasswordHash(hashedPassword);
            w4.setServices(new java.util.ArrayList<>(List.of("AC Service")));

            Worker w5 = new Worker("Priya Singh", "House Maid", "9876543214", 4.9, "https://randomuser.me/api/portraits/women/44.jpg", "4 years", "Professional home cleaning services");
            w5.setEmail("priya@gharfix.com");
            w5.setPasswordHash(hashedPassword);
            w5.setServices(new java.util.ArrayList<>(List.of("House Maid")));

            Worker w6 = new Worker("Vikram Joshi", "Laundry", "9876543215", 4.4, "https://randomuser.me/api/portraits/men/75.jpg", "3 years", "Quality laundry and dry cleaning");
            w6.setEmail("vikram@gharfix.com");
            w6.setPasswordHash(hashedPassword);
            w6.setServices(new java.util.ArrayList<>(List.of("Laundry")));

            workerRepository.saveAll(List.of(w1, w2, w3, w4, w5, w6));
            log.info("Default workers seeded successfully (6 items, all with email/password credentials and services).");
        } else {
            // Migrate existing workers to multi-service structure if empty
            List<Worker> existingWorkers = workerRepository.findAll();
            for (Worker w : existingWorkers) {
                if (w.getServices() == null || w.getServices().isEmpty()) {
                    if (w.getService() != null && !w.getService().isBlank()) {
                        String[] parts = w.getService().split(",");
                        List<String> list = new java.util.ArrayList<>();
                        for (String p : parts) {
                            if (!p.trim().isEmpty()) {
                                list.add(p.trim());
                            }
                        }
                        w.setServices(list);
                        workerRepository.save(w);
                        log.info("Migrated existing worker {} to services: {}", w.getEmail(), list);
                    }
                }
            }
        }
    }

    private void seedUsersAndReviews() {
        if (userRepository.count() == 0) {
            User demoUser = new User("Rahul Verma", "demo@gharfix.com", "9876500000", passwordEncoder.encode("password123"));
            demoUser = userRepository.save(demoUser);
            log.info("Demo user created: demo@gharfix.com / password123");

            if (reviewRepository.count() == 0) {
                List<Worker> workers = workerRepository.findAll();
                if (workers.size() >= 3) {
                    reviewRepository.save(new Review(demoUser, workers.get(0), 5.0, "Great service! Electrician arrived on time."));
                    reviewRepository.save(new Review(demoUser, workers.get(1), 4.5, "Very professional plumber, fixed everything quickly."));
                    reviewRepository.save(new Review(demoUser, workers.get(2), 4.7, "Affordable and reliable service."));
                    log.info("Default reviews seeded successfully.");
                }
            }
        }
    }

    private void importFromSqliteIfPresent() {
        File sqliteFile = new File("instance/gharfix.db");
        if (!sqliteFile.exists()) {
            sqliteFile = new File("gharfix.db");
        }
        if (!sqliteFile.exists()) {
            return;
        }

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteFile.getAbsolutePath())) {
            // Import existing users if any
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id, name, email, phone, password_hash FROM user")) {
                while (rs.next()) {
                    String email = rs.getString("email");
                    if (!userRepository.existsByEmail(email)) {
                        User u = new User();
                        u.setName(rs.getString("name"));
                        u.setEmail(email);
                        u.setPhone(rs.getString("phone"));
                        u.setPasswordHash(rs.getString("password_hash"));
                        userRepository.save(u);
                        log.info("Imported user from SQLite: {}", email);
                    }
                }
            } catch (SQLException ignored) {
            }

            // Import existing bookings if any
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM booking")) {
                while (rs.next()) {
                    Long userId = rs.getLong("user_id");
                    Long workerId = rs.getLong("worker_id");
                    var userOpt = userRepository.findById(userId);
                    var workerOpt = workerRepository.findById(workerId);
                    if (userOpt.isPresent()) {
                        Booking b = new Booking();
                        b.setUser(userOpt.get());
                        b.setWorker(workerOpt.orElse(null));
                        b.setServiceName(rs.getString("service_name"));
                        b.setAddress(rs.getString("address"));
                        b.setCity(rs.getString("city"));
                        try {
                            b.setBookingDate(LocalDate.parse(rs.getString("booking_date")));
                            b.setBookingTime(LocalTime.parse(rs.getString("booking_time")));
                        } catch (Exception e) {
                            b.setBookingDate(LocalDate.now());
                            b.setBookingTime(LocalTime.of(10, 0));
                        }
                        b.setStatus(rs.getString("status"));
                        bookingRepository.save(b);
                    }
                }
            } catch (SQLException ignored) {
            }

        } catch (Exception e) {
            log.warn("SQLite migration check completed with notice: {}", e.getMessage());
        }
    }
}
