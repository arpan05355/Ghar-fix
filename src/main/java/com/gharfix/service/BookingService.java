package com.gharfix.service;

import com.gharfix.dto.BookingRequestDto;
import com.gharfix.entity.Booking;
import com.gharfix.entity.User;
import com.gharfix.entity.Worker;
import com.gharfix.repository.BookingRepository;
import com.gharfix.repository.WorkerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final WorkerRepository workerRepository;

    public BookingService(BookingRepository bookingRepository, WorkerRepository workerRepository) {
        this.bookingRepository = bookingRepository;
        this.workerRepository = workerRepository;
    }

    public List<Booking> getUserBookings(Long userId) {
        return bookingRepository.findByUserIdWithWorkerOrderByDateDesc(userId);
    }

    public List<Booking> getRequestedBookingsForService(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            return java.util.Collections.emptyList();
        }
        return bookingRepository.findByStatusAndServiceNameIgnoreCase("requested", serviceName.trim());
    }

    public List<Booking> getRequestedBookingsForServices(List<String> services) {
        if (services == null || services.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<String> lowerServices = services.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim().toLowerCase())
                .toList();
        if (lowerServices.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return bookingRepository.findByStatusAndServiceNameInIgnoreCase("requested", lowerServices);
    }

    public List<Booking> getAcceptedBookingsForWorker(Long workerId) {
        return bookingRepository.findByWorkerIdOrderByBookingDateDesc(workerId);
    }

    @Transactional
    public Booking createBooking(User user, BookingRequestDto request) {
        LocalDate bookingDate = LocalDate.parse(request.getDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        LocalTime bookingTime = LocalTime.parse(request.getTime(), DateTimeFormatter.ofPattern("HH:mm"));

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setServiceName(request.getService());
        booking.setAddress(request.getAddress());
        booking.setCity(request.getCity());
        booking.setBookingDate(bookingDate);
        booking.setBookingTime(bookingTime);

        // If a specific worker is selected, assign them; otherwise leave as open request
        if (request.getWorkerId() != null) {
            Worker worker = workerRepository.findById(request.getWorkerId())
                    .orElse(null);
            if (worker != null) {
                booking.setWorker(worker);
                booking.setStatus("pending");
            } else {
                booking.setStatus("requested");
            }
        } else {
            booking.setStatus("requested");
        }

        return bookingRepository.save(booking);
    }

    @Transactional
    public Booking acceptBooking(Long bookingId, Worker worker) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found."));

        if (!"requested".equals(booking.getStatus())) {
            throw new IllegalStateException("This request was already accepted by another professional.");
        }

        booking.setWorker(worker);
        booking.setStatus("accepted");
        return bookingRepository.save(booking);
    }

    @Transactional
    public Booking save(Booking booking) {
        return bookingRepository.save(booking);
    }
}
