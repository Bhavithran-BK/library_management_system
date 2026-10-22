package com.library.repository;

import com.library.model.Reservation;
import com.library.model.ReservationStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends MongoRepository<Reservation, String> {

    Optional<Reservation> findByReservationId(String reservationId);

    List<Reservation> findByMemberIdOrderByCreatedAtDesc(String memberId);

    List<Reservation> findByBookIdAndStatusOrderByQueuePositionAsc(String bookId, ReservationStatus status);

    List<Reservation> findByStatusOrderByCreatedAtDesc(ReservationStatus status);

    long countByBookIdAndStatus(String bookId, ReservationStatus status);

    long countByStatus(ReservationStatus status);

    boolean existsByMemberIdAndBookIdAndStatus(String memberId, String bookId, ReservationStatus status);
}
