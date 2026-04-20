package com.stilov.springboot_practice_2503;

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final ReservationRepository repository;

    public ReservationService(ReservationRepository repository) {
        this.repository = repository;
    }


    public List<Reservation> getAllReservations() {

        List<ReservationEntity> allEntities = repository.findAll();

        return allEntities.stream()
                .map(this::toDomainReservation).toList();
    }



    public Reservation createReservation(Reservation reservationToCreate) {
        if(reservationToCreate.id() != null){
            throw new IllegalArgumentException("Reservation id must be null");
        }
        if(reservationToCreate.status() != null){
            throw new IllegalArgumentException("Reservation status must be null");
        }
        var entityToSave = new ReservationEntity(
                null,
                reservationToCreate.userId(),
                reservationToCreate.roomId(),
                reservationToCreate.startDate(),
                reservationToCreate.endDate(),
                ReservationStatus.PENDING
        );
        var savedEntity = repository.save(entityToSave);
        return toDomainReservation(savedEntity);
    }


    public Reservation upadateReservation(Long id, Reservation reservationToUpdate) {
        if(!repository.existsById(id)){
            throw new NoSuchElementException("Reservation not found by id: " + id);
        }

        var reservationEntity = repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Reservation not found by id: " + id));

        if(reservationEntity.getStatus() != ReservationStatus.PENDING){
            throw new IllegalStateException("Reservation status cannot be modified, status is=" + reservationEntity.getStatus());
        }

        var ReservationToSave = new ReservationEntity(
                reservationEntity.getId(),
                reservationToUpdate.userId(),
                reservationToUpdate.roomId(),
                reservationToUpdate.startDate(),
                reservationToUpdate.endDate(),
                ReservationStatus.PENDING
        );
        var updatedReservation = repository.save(ReservationToSave);
        return toDomainReservation(updatedReservation);

    }


    public Reservation getReservationByUserId(Long id) {
        ReservationEntity reservationEntity = repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Reservation not found by id: " + id));

        return toDomainReservation(reservationEntity);
    };


    @Transactional

    public void cancelReservation(Long id) {
        if(!repository.existsById(id)){
            throw new NoSuchElementException("Reservation not found by id: " + id);
        }
        repository.setStatus(id, ReservationStatus.REJECTED);
        log.info("Reservation has been cancelled by user: id={}", id);
    }


    public Reservation approveReservation(Long id) {
        var reservationEntity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Reservation not found by id: " + id));


        if(reservationEntity.getStatus() != ReservationStatus.PENDING){
            throw new IllegalStateException("Cannot approve reservation: status=" + reservationEntity.getStatus());
        }
        var isConflict = isReservationConflict(reservationEntity);
        if(isConflict){
            throw new IllegalStateException("Cannot approve reservation: isConflict");
        }

        reservationEntity.setStatus(ReservationStatus.APPROVED);
        repository.save(reservationEntity);

        return toDomainReservation(reservationEntity);
    }


    private boolean isReservationConflict(ReservationEntity reservation) {
        var allReservations = repository.findAll();

        for(ReservationEntity existingReservation: allReservations){
            if(reservation.getId().equals(existingReservation.getId())){
                continue;
            }
            if(!reservation.getRoomId().equals(existingReservation.getRoomId())){
                continue;
            }
            if(!existingReservation.getStatus().equals(ReservationStatus.APPROVED)){
                continue;
            }
            if(reservation.getStartDate().isBefore(existingReservation.getEndDate()) && existingReservation.getStartDate().isBefore(reservation.getEndDate())){
                return true;
            }
        }
        return false;
    }

    private Reservation toDomainReservation(ReservationEntity reservation) {
        return new Reservation(
                reservation.getId(),
                reservation.getUserId(),
                reservation.getRoomId(),
                reservation.getStartDate(),
                reservation.getEndDate(),
                reservation.getStatus()
        );
    }
}
