package com.library.controller;

import com.library.dto.ApiResponse;
import com.library.dto.BorrowRequest;
import com.library.model.Loan;
import com.library.service.LoanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/loans")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Loan>> borrowBook(@Valid @RequestBody BorrowRequest request,
                                                        Authentication authentication) {
        String currentMemberId = (String) authentication.getPrincipal();
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin && !currentMemberId.equals(request.getMemberId())) {
            throw new AccessDeniedException("You can only borrow books for your own account");
        }

        Loan loan = loanService.borrowBook(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Book borrowed successfully with Loan ID: " + loan.getLoanId(), loan));
    }

    @PostMapping("/{loanId}/return")
    public ResponseEntity<ApiResponse<Loan>> returnBook(@PathVariable String loanId) {
        Loan loan = loanService.returnBook(loanId);
        return ResponseEntity.ok(ApiResponse.success("Book returned successfully", loan));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Loan>>> getLoans(
            @RequestParam(required = false) String memberId,
            Authentication authentication) {
        String currentMemberId = (String) authentication.getPrincipal();
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (memberId != null && !memberId.isBlank()) {
            if (!isAdmin && !currentMemberId.equals(memberId)) {
                throw new AccessDeniedException("Unauthorized to view loans of other members");
            }
            return ResponseEntity.ok(ApiResponse.success(loanService.getMemberLoans(memberId)));
        }

        if (isAdmin) {
            return ResponseEntity.ok(ApiResponse.success(loanService.getAllLoans()));
        } else {
            return ResponseEntity.ok(ApiResponse.success(loanService.getMemberLoans(currentMemberId)));
        }
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<Loan>>> getActiveLoans() {
        return ResponseEntity.ok(ApiResponse.success(loanService.getAllActiveLoans()));
    }
}
