package com.stilov.springboot_practice_2503.reservations;

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final ReservationRepository repository;

    private final ReservationMapper mapper;

    public ReservationService(ReservationRepository repository, ReservationMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }


    public List<Reservation> searchAllByFilter(
            ReservationSearchFilter filter
    ) {
    int pageSize = filter.pageSize() != null
                                    ? filter.pageSize() : 10;
    int pageNumber = filter.pageNumber() != null
                ? filter.pageNumber() : 0;
    var pageable = Pageable
                .ofSize(pageSize)
                .withPage(pageNumber);
        List<ReservationEntity> allEntities = repository.searchAllByFilter(filter.roomId(), filter.userId(), pageable);

        return allEntities.stream()
                .map(mapper::toDomain).toList();
    }



    public Reservation createReservation(Reservation reservationToCreate) {
        if(reservationToCreate.status() != null){
            throw new IllegalArgumentException("Reservation status must be null");
        }
        if(!reservationToCreate.endDate().isAfter(reservationToCreate.startDate())){
            throw new IllegalArgumentException("Reservation start date must be after end date");
        }

        var entityToSave = mapper.toEntity(reservationToCreate);
        entityToSave.setStatus(ReservationStatus.PENDING);
        var savedEntity = repository.save(entityToSave);
        return mapper.toDomain(savedEntity);
    }


    public Reservation upadateReservation(
            Long id,
            Reservation reservationToUpdate)
    {
        var reservationEntity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Reservation not found by id: " + id));

        if(reservationEntity.getStatus() != ReservationStatus.PENDING){
            throw new IllegalStateException("Reservation status cannot be modified, status is=" + reservationEntity.getStatus());
        }
        if(!reservationToUpdate.endDate().isAfter(reservationToUpdate.startDate())){
            throw new IllegalArgumentException("Reservation start date must be after end date");
        }

        var reservationToSave = mapper.toEntity(reservationToUpdate);
        reservationToSave.setId(reservationEntity.getId());
        reservationToSave.setStatus(ReservationStatus.PENDING);

        var updatedReservation = repository.save(reservationToSave);

        return mapper.toDomain(updatedReservation);

    }


    public Reservation getReservationByUserId(Long id) {
        ReservationEntity reservationEntity = repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Reservation not found by id: " + id));

        return mapper.toDomain(reservationEntity);
    };


    @Transactional
    public void cancelReservation(Long id) {
        var reservation = repository.findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("Reservation not found by id: " + id));
        if(reservation.getStatus().equals(ReservationStatus.APPROVED)){
            throw new IllegalStateException("Reservation status cannot be cancelled, status is approved");
        }
        if(reservation.getStatus().equals(ReservationStatus.CANCELED)){
            throw new IllegalStateException("Reservation status cannot be cancelled, status is already cancelled");
        }
        repository.setStatus(id, ReservationStatus.CANCELED);
        log.info("Reservation has been cancelled by user: id={}", id);
    }


    public Reservation approveReservation(Long id) {
        var reservationEntity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Reservation not found by id: " + id));

        if(reservationEntity.getStatus() != ReservationStatus.PENDING){
            throw new IllegalStateException("Cannot approve reservation: status=" + reservationEntity.getStatus());
        }
        var isConflict = isReservationConflict(
                reservationEntity.getRoomId(),
                reservationEntity.getStartDate(),
                reservationEntity.getEndDate()
        );
        if(isConflict){
            throw new IllegalStateException("Cannot approve reservation: isConflict");
        }

        reservationEntity.setStatus(ReservationStatus.APPROVED);
        repository.save(reservationEntity);

        return mapper.toDomain(reservationEntity);
    }


    private boolean isReservationConflict(
            Long roomId,
            LocalDate startDate,
            LocalDate endDate) {
        List<Long> conflictingIds = repository.findConflictReservartionsIds(roomId, startDate, endDate, ReservationStatus.APPROVED);
        if(conflictingIds.isEmpty()){
            return false;
        }
        log.info("Conflict with ids = {}", conflictingIds);
        return true;
    }


}
