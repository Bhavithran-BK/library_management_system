package com.library.service;

import com.library.dto.ReservationRequest;
import com.library.exception.BadRequestException;
import com.library.exception.ConflictException;
import com.library.exception.ResourceNotFoundException;
import com.library.idgenerator.IdGeneratorService;
import com.library.model.Book;
import com.library.model.LoanStatus;
import com.library.model.Reservation;
import com.library.model.ReservationStatus;
import com.library.repository.BookRepository;
import com.library.repository.LoanRepository;
import com.library.repository.MemberRepository;
import com.library.repository.ReservationRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final BookRepository bookRepository;
    private final MemberRepository memberRepository;
    private final LoanRepository loanRepository;
    private final IdGeneratorService idGeneratorService;

    public ReservationService(ReservationRepository reservationRepository,
                              BookRepository bookRepository,
                              MemberRepository memberRepository,
                              LoanRepository loanRepository,
                              IdGeneratorService idGeneratorService) {
        this.reservationRepository = reservationRepository;
        this.bookRepository = bookRepository;
        this.memberRepository = memberRepository;
        this.loanRepository = loanRepository;
        this.idGeneratorService = idGeneratorService;
    }

    public Reservation createReservation(ReservationRequest request) {
        String memberId = request.getMemberId().trim();
        String bookId = request.getBookId().trim();

        if (!memberRepository.existsByMemberId(memberId)) {
            throw new ResourceNotFoundException("Member not found with ID: " + memberId);
        }

        Book book = bookRepository.findByBookId(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found with ID: " + bookId));

        if (loanRepository.existsByMemberIdAndBookIdAndStatus(memberId, bookId, LoanStatus.ACTIVE)) {
            throw new BadRequestException("Member already has an active loan for this book");
        }

        if (reservationRepository.existsByMemberIdAndBookIdAndStatus(memberId, bookId, ReservationStatus.PENDING)) {
            throw new ConflictException("Member already has a pending reservation for this book");
        }

        long existingCount = reservationRepository.countByBookIdAndStatus(bookId, ReservationStatus.PENDING);
        int queuePosition = (int) existingCount + 1;

        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            String reservationId = idGeneratorService.generateReservationId();
            Reservation reservation = new Reservation(reservationId, memberId, bookId, queuePosition);
            try {
                return reservationRepository.save(reservation);
            } catch (DuplicateKeyException ex) {
                if (i == maxRetries - 1) {
                    throw new ConflictException("Failed to generate unique reservation ID: " + ex.getMessage());
                }
            }
        }

        throw new IllegalStateException("Failed to create reservation");
    }

    public void cancelReservation(String reservationId, String currentMemberId, boolean isAdmin) {
        Reservation reservation = reservationRepository.findByReservationId(reservationId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with ID: " + reservationId));

        if (!isAdmin && !reservation.getMemberId().equals(currentMemberId)) {
            throw new BadRequestException("You are not authorized to cancel this reservation");
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);

        // Reorder queue for remaining pending reservations
        reorderQueue(reservation.getBookId());
    }

    public List<Reservation> getMemberReservations(String memberId) {
        return reservationRepository.findByMemberIdOrderByCreatedAtDesc(memberId.trim());
    }

    public List<Reservation> getBookReservations(String bookId) {
        return reservationRepository.findByBookIdAndStatusOrderByQueuePositionAsc(bookId.trim(), ReservationStatus.PENDING);
    }

    public List<Reservation> getAllReservations() {
        return reservationRepository.findAll();
    }

    private void reorderQueue(String bookId) {
        List<Reservation> pending = reservationRepository.findByBookIdAndStatusOrderByQueuePositionAsc(bookId, ReservationStatus.PENDING);
        int pos = 1;
        for (Reservation r : pending) {
            r.setQueuePosition(pos++);
            reservationRepository.save(r);
        }
    }
}
