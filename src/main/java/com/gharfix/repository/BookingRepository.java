package com.gharfix.repository;

import com.gharfix.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("SELECT b FROM Booking b LEFT JOIN FETCH b.worker WHERE b.user.id = :userId ORDER BY b.bookingDate DESC, b.bookingTime DESC")
    List<Booking> findByUserIdWithWorkerOrderByDateDesc(@Param("userId") Long userId);

    List<Booking> findByStatusAndServiceNameIgnoreCase(String status, String serviceName);

    List<Booking> findByWorkerIdOrderByBookingDateDesc(Long workerId);
}
