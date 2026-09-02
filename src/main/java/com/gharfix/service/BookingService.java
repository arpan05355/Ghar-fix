package com.gharfix.service;

import com.gharfix.dto.BookingRequestDto;
import com.gharfix.entity.Booking;
import com.gharfix.entity.User;
import com.gharfix.entity.Worker;
import com.gharfix.repository.BookingRepository;
import com.gharfix.repository.WorkerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@SuppressWarnings("null")
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
        return getRequestedBookingsForService(serviceName, null);
    }

    public List<Booking> getRequestedBookingsForService(String serviceName, Long workerId) {
        if (serviceName == null || serviceName.isBlank()) {
            return java.util.Collections.emptyList();
        }
        List<Booking> bookings = bookingRepository.findByStatusAndServiceNameIgnoreCase("requested", serviceName.trim());
        return bookings.stream()
                .filter(b -> b.getWorker() == null
                        || (workerId != null && b.getWorker().getId().equals(workerId))
                        || (b.getNegotiationStatus() != null && "NONE".equalsIgnoreCase(b.getNegotiationStatus())))
                .toList();
    }

    public List<Booking> getRequestedBookingsForServices(List<String> services) {
        return getRequestedBookingsForServices(services, null);
    }

    public List<Booking> getRequestedBookingsForServices(List<String> services, Long workerId) {
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
        List<Booking> bookings = bookingRepository.findByStatusAndServiceNameInIgnoreCase("requested", lowerServices);
        return bookings.stream()
                .filter(b -> b.getWorker() == null
                        || (workerId != null && b.getWorker().getId().equals(workerId))
                        || (b.getNegotiationStatus() != null && "NONE".equalsIgnoreCase(b.getNegotiationStatus())))
                .toList();
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

        if (booking.getWorker() != null
                && !booking.getWorker().getId().equals(worker.getId())
                && booking.getNegotiationStatus() != null
                && !"NONE".equalsIgnoreCase(booking.getNegotiationStatus())) {
            throw new IllegalStateException("This request is already being negotiated by another professional.");
        }

        booking.setWorker(worker);
        booking.setStatus("accepted");
        return bookingRepository.save(booking);
    }

    @Transactional
    public Booking save(Booking booking) {
        return bookingRepository.save(booking);
    }

    public List<Booking> getPendingNegotiationsForUser(Long userId) {
        return bookingRepository.findPendingNegotiationsForUser(userId);
    }

    public List<Booking> getPendingNegotiationsForWorker(Long workerId) {
        return bookingRepository.findPendingNegotiationsForWorker(workerId);
    }

    @Transactional
    public Booking proposePriceByWorker(Long bookingId, Worker worker, BigDecimal price) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found."));

        if (!"requested".equals(booking.getStatus())) {
            throw new IllegalStateException("This request is no longer open.");
        }

        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Proposed price must be greater than zero.");
        }

        if (booking.getWorker() != null
                && !booking.getWorker().getId().equals(worker.getId())
                && booking.getNegotiationStatus() != null
                && !"NONE".equalsIgnoreCase(booking.getNegotiationStatus())) {
            throw new IllegalStateException("This request is already being negotiated by another professional.");
        }

        if (booking.getNegotiationStatus() != null
                && !"NONE".equalsIgnoreCase(booking.getNegotiationStatus())
                && booking.getWorker() != null) {
            throw new IllegalStateException("A negotiation is already active on this request.");
        }

        booking.setWorker(worker);
        booking.setProposedPrice(price);
        booking.setProposedBy("WORKER");
        booking.setNegotiationStatus("PENDING_USER");
        return bookingRepository.save(booking);
    }

    @Transactional
    public Booking acceptBookingPriceByUser(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found."));

        if (booking.getUser() == null || !booking.getUser().getId().equals(userId)) {
            throw new IllegalStateException("You are not authorized to accept this booking.");
        }

        if (!"requested".equals(booking.getStatus())) {
            throw new IllegalStateException("This request was already accepted or completed.");
        }

        if (booking.getWorker() == null) {
            throw new IllegalStateException("No worker is assigned to this proposal.");
        }

        booking.setStatus("accepted");
        booking.setNegotiationStatus("AGREED");
        return bookingRepository.save(booking);
    }

    @Transactional
    public Booking counterPriceByUser(Long bookingId, Long userId, BigDecimal price) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found."));

        if (booking.getUser() == null || !booking.getUser().getId().equals(userId)) {
            throw new IllegalStateException("You are not authorized to counter this booking.");
        }

        if (!"requested".equals(booking.getStatus())) {
            throw new IllegalStateException("This request is no longer open for negotiation.");
        }

        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Counter price must be greater than zero.");
        }

        booking.setProposedPrice(price);
        booking.setProposedBy("USER");
        booking.setNegotiationStatus("PENDING_WORKER");
        return bookingRepository.save(booking);
    }

    @Transactional
    public Booking acceptBookingPriceByWorker(Long bookingId, Worker worker) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found."));

        if (!"requested".equals(booking.getStatus())) {
            throw new IllegalStateException("This request was already accepted by another professional.");
        }

        if (booking.getWorker() == null || !booking.getWorker().getId().equals(worker.getId())) {
            throw new IllegalStateException("You are not part of the negotiation on this request.");
        }

        booking.setWorker(worker);
        booking.setStatus("accepted");
        booking.setNegotiationStatus("AGREED");
        return bookingRepository.save(booking);
    }

    @Transactional
    public Booking counterPriceByWorker(Long bookingId, Worker worker, BigDecimal price) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found."));

        if (!"requested".equals(booking.getStatus())) {
            throw new IllegalStateException("This request is no longer open for negotiation.");
        }

        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Counter price must be greater than zero.");
        }

        if (booking.getWorker() == null || !booking.getWorker().getId().equals(worker.getId())) {
            throw new IllegalStateException("You are not part of the negotiation on this request.");
        }

        booking.setWorker(worker);
        booking.setProposedPrice(price);
        booking.setProposedBy("WORKER");
        booking.setNegotiationStatus("PENDING_USER");
        return bookingRepository.save(booking);
    }
}
