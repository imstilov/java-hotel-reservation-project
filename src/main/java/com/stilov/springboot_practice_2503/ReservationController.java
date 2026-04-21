package com.stilov.springboot_practice_2503;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/reservation")
public class ReservationController {

    private static final Logger log = LoggerFactory.getLogger(ReservationController.class);

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Reservation> getReservationByUserId(@PathVariable("id") Long id) {
            log.info("Called getReservationById with id " + id);
            return ResponseEntity.ok().body(reservationService.getReservationByUserId(id));
    }

    @GetMapping
    public ResponseEntity<List<Reservation>> getAllReservations() {
        log.info("Called getAllReservations");
        return ResponseEntity.ok(reservationService.getAllReservations());
    }

    @PostMapping
    public ResponseEntity<Reservation> createReservation(@RequestBody Reservation reservationToCreate){
        log.info("Called createReservation");
        return ResponseEntity.ok().header("test-header", "123").body(reservationService.createReservation(reservationToCreate));
    };

    @PutMapping("/{id}")
    public ResponseEntity<Reservation> updateReservation(@PathVariable("id") Long id, @RequestBody Reservation reservationToUpdate){
        log.info("Called updateReservation id={}, reservationToUpdate={}", id, reservationToUpdate);
        var updated = reservationService.upadateReservation(id, reservationToUpdate);
        return ResponseEntity.ok().body(updated);

    }

    @DeleteMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelReservation(@PathVariable("id") Long id){
        log.info("Called cancelReservation id={}", id);
            reservationService.cancelReservation(id);
            return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Reservation> approveReservation(@PathVariable("id") Long id){
        log.info("Called approveReservation id={}", id);
        var reservation = reservationService.approveReservation(id);
        return ResponseEntity.ok(reservation);
    }
}
