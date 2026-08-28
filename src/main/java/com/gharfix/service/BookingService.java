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

    @Transactional
    public Booking createBooking(User user, BookingRequestDto request) {
        if (request.getWorkerId() == null) {
            throw new IllegalArgumentException("Please select a worker.");
        }

        Worker worker = workerRepository.findById(request.getWorkerId())
                .orElseThrow(() -> new IllegalArgumentException("Worker not found."));

        LocalDate bookingDate = LocalDate.parse(request.getDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        LocalTime bookingTime = LocalTime.parse(request.getTime(), DateTimeFormatter.ofPattern("HH:mm"));

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setWorker(worker);
        booking.setServiceName(request.getService());
        booking.setAddress(request.getAddress());
        booking.setCity(request.getCity());
        booking.setBookingDate(bookingDate);
        booking.setBookingTime(bookingTime);
        booking.setStatus("pending");

        return bookingRepository.save(booking);
    }

    @Transactional
    public Booking save(Booking booking) {
        return bookingRepository.save(booking);
    }
}
