package com.library.controller;

import com.library.dto.ApiResponse;
import com.library.dto.ReservationRequest;
import com.library.model.Reservation;
import com.library.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/api/reservations")
    public ResponseEntity<ApiResponse<Reservation>> createReservation(
            @Valid @RequestBody ReservationRequest request,
            Authentication authentication) {
        String currentMemberId = (String) authentication.getPrincipal();
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin && !currentMemberId.equals(request.getMemberId())) {
            throw new AccessDeniedException("You can only create reservations for your own account");
        }

        Reservation res = reservationService.createReservation(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Reservation placed successfully at queue position #" + res.getQueuePosition(), res));
    }

    @GetMapping("/api/members/{memberId}/reservations")
    public ResponseEntity<ApiResponse<List<Reservation>>> getMemberReservations(
            @PathVariable String memberId,
            Authentication authentication) {
        String currentMemberId = (String) authentication.getPrincipal();
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin && !currentMemberId.equals(memberId)) {
            throw new AccessDeniedException("Unauthorized to view reservations of other members");
        }

        List<Reservation> list = reservationService.getMemberReservations(memberId);
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @DeleteMapping("/api/reservations/{reservationId}")
    public ResponseEntity<ApiResponse<Void>> cancelReservation(
            @PathVariable String reservationId,
            Authentication authentication) {
        String currentMemberId = (String) authentication.getPrincipal();
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        reservationService.cancelReservation(reservationId, currentMemberId, isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Reservation cancelled successfully", null));
    }

    @GetMapping("/api/reservations")
    public ResponseEntity<ApiResponse<List<Reservation>>> getAllReservations(Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin) {
            throw new AccessDeniedException("Admin access required to view all reservations");
        }
        return ResponseEntity.ok(ApiResponse.success(reservationService.getAllReservations()));
    }
}
