package com.library.controller;

import com.library.dto.ApiResponse;
import com.library.dto.PayFineRequest;
import com.library.model.FineTransaction;
import com.library.service.FineService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping
public class FineController {

    private final FineService fineService;

    public FineController(FineService fineService) {
        this.fineService = fineService;
    }

    @GetMapping("/api/members/{memberId}/fines")
    public ResponseEntity<ApiResponse<List<FineTransaction>>> getMemberFines(
            @PathVariable String memberId,
            Authentication authentication) {
        String currentMemberId = (String) authentication.getPrincipal();
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin && !currentMemberId.equals(memberId)) {
            throw new AccessDeniedException("Unauthorized to view fines of other members");
        }

        List<FineTransaction> list = fineService.getMemberFines(memberId);
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @PostMapping("/api/fines/{id}/pay")
    public ResponseEntity<ApiResponse<FineTransaction>> payFine(
            @PathVariable("id") String transactionId,
            @RequestBody(required = false) PayFineRequest request) {
        if (request == null) {
            request = new PayFineRequest();
        }
        FineTransaction paid = fineService.payFine(transactionId, request);
        return ResponseEntity.ok(ApiResponse.success("Fine payment simulated and processed successfully", paid));
    }

    @GetMapping("/api/fines")
    public ResponseEntity<ApiResponse<List<FineTransaction>>> getAllFines(Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin) {
            throw new AccessDeniedException("Admin access required to view all fine transactions");
        }
        return ResponseEntity.ok(ApiResponse.success(fineService.getAllFines()));
    }
}
