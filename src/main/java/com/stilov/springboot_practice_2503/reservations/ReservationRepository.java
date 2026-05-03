package com.stilov.springboot_practice_2503.reservations;

import com.stilov.springboot_practice_2503.entities.ReservationEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ReservationRepository extends JpaRepository<ReservationEntity, Long> {

    @Modifying
    @Query("""
            update ReservationEntity r 
            set r.status = :status
            where r.id = :id
            """)
    void setStatus(
            @Param("id") Long id,
            @Param("status") ReservationStatus reservationStatus);

    @Query("""
            SELECT r.id from ReservationEntity r
                WHERE r.roomId = :roomId
                AND :startDate < r.endDate
                AND r.startDate < :endDate
                AND r.status = :status
""")
    List<Long> findConflictReservartionsIds(
            @Param("roomId") Long roomId,
            @Param("startDate")LocalDate startDate,
            @Param("endDate")LocalDate endDate,
            @Param("status")ReservationStatus status);

    @Query("""
    SELECT r FROM ReservationEntity r
                WHERE (:roomId IS NULL OR r.roomId = :roomId)
                AND (:userId IS NULL OR r.userId = :userId)
""")
    List<ReservationEntity> searchAllByFilter(
            @Param("roomId") Long roomId,
            @Param("userId") Long userId,
            Pageable pageable
    );

    @Query("""
    SELECT r.roomId FROM ReservationEntity r
    GROUP BY r.roomId
    ORDER BY COUNT(r) DESC
    LIMIT 3
""")
    List<Long> findTop3PopularRooms();

    @Query("""
    SELECT r FROM ReservationEntity r
        WHERE r.userId = :userId
        AND r.status = :status
""")
    List<ReservationEntity> groupByUserIdAndStatus(
            @Param("userId") Long userId,
            @Param("status") ReservationStatus status,
            Pageable pageable
    );

    @Query("""
    SELECT COUNT(r) FROM ReservationEntity r
    WHERE r.startDate >= :startDate
""")
    Integer countByPeriod(
            @Param("startDate") LocalDate startDate
    );

}

