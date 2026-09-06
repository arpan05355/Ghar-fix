package com.gharfix.service;

import com.gharfix.entity.ServiceComplaint;
import com.gharfix.entity.User;
import com.gharfix.repository.ServiceComplaintRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceComplaintServiceTest {

    @Mock
    private ServiceComplaintRepository complaintRepository;

    private ServiceComplaintService complaintService;
    private User testUser;

    @BeforeEach
    void setUp() {
        complaintService = new ServiceComplaintService(complaintRepository);
        testUser = new User("Priya Patel", "priya@example.com", "9876543210", "pass");
        testUser.setId(20L);
    }

    @Test
    void testRegisterComplaint_Success() {
        when(complaintRepository.save(any(ServiceComplaint.class))).thenAnswer(inv -> inv.getArgument(0));

        ServiceComplaint complaint = complaintService.registerComplaint(
                testUser, null, "Electrician", "Short circuit after work", "HIGH"
        );

        assertNotNull(complaint);
        assertEquals("Electrician", complaint.getServiceCategory());
        assertEquals("Short circuit after work", complaint.getDescription());
        assertEquals("HIGH", complaint.getSeverity());
        assertEquals("OPEN", complaint.getStatus());
        assertTrue(complaint.getTicketNumber().startsWith("GF-CMP-"));
        verify(complaintRepository).save(any(ServiceComplaint.class));
    }

    @Test
    void testRegisterComplaint_EmptyDescription_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                complaintService.registerComplaint(testUser, null, "Plumber", "   ", "LOW")
        );
    }

    @Test
    void testGetUserComplaints() {
        ServiceComplaint c = new ServiceComplaint(testUser, null, "AC Service", "Not cooling", "MEDIUM", "GF-CMP-1111");
        when(complaintRepository.findByUserIdOrderByCreatedAtDesc(20L)).thenReturn(List.of(c));

        List<ServiceComplaint> list = complaintService.getUserComplaints(20L);
        assertEquals(1, list.size());
        assertEquals("GF-CMP-1111", list.get(0).getTicketNumber());
    }
}
