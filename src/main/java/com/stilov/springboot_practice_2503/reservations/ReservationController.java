package com.stilov.springboot_practice_2503.reservations;

import com.stilov.springboot_practice_2503.search_filters.GroupByUserAndStatus;
import com.stilov.springboot_practice_2503.search_filters.ReservationSearchFilter;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
            return ResponseEntity.ok().body(reservationService.getReservationById(id));
    }

    @GetMapping
    public ResponseEntity<List<Reservation>> getAllReservations(
            @RequestParam(name = "roomId", required = false) Long roomId,
            @RequestParam(name = "userId", required = false)Long userId,
            @RequestParam(name = "pageSize", required = false)Integer pageSize,
            @RequestParam(name = "pageNumber", required = false)Integer pageNumber
    ) {
        log.info("Called getAllReservations");
        var filter = new ReservationSearchFilter(
                roomId,
                userId,
                pageSize,
                pageNumber
        );
        return ResponseEntity.ok(reservationService.searchAllByFilter(filter));
    }

    @GetMapping("/groupby")
    public ResponseEntity<List<Reservation>> groupByUserIdAndStatus(
            @RequestParam(name = "userId") Long userId,
            @RequestParam(name = "status") ReservationStatus status,
            @RequestParam(name = "pageSize", required = false) Integer pageSize,
            @RequestParam(name = "pageNumber", required = false) Integer pageNumber
    ){
        log.info("Called groupByStatusAndUserId");
        var filter = new GroupByUserAndStatus(
                userId,
                status,
                pageSize,
                pageNumber
        );
        return ResponseEntity.ok(reservationService.groupByStatusAndUserId(filter));
    }


    @PostMapping
    public ResponseEntity<Reservation> createReservation(@RequestBody @Valid Reservation reservationToCreate){
        log.info("Called createReservation");
        return ResponseEntity.ok().header("test-header", "123").body(reservationService.createReservation(reservationToCreate));
    };

    @PutMapping("/{id}")
    public ResponseEntity<Reservation> updateReservation(@PathVariable("id") Long id, @RequestBody Reservation reservationToUpdate){
        log.info("Called updateReservation id={}, reservationToUpdate={}", id, reservationToUpdate);
        var updated = reservationService.updateReservation(id, reservationToUpdate);
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
