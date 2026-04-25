package com.stilov.springboot_practice_2503.reservations;

import com.stilov.springboot_practice_2503.reservations.stats.ReservationStats;
import com.stilov.springboot_practice_2503.search_filters.GroupByUserAndStatus;
import com.stilov.springboot_practice_2503.search_filters.ReservationSearchFilter;
import com.stilov.springboot_practice_2503.web.ApiResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/reservation")
public class ReservationController {

    private static final Logger log = LoggerFactory.getLogger(ReservationController.class);

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Reservation>> getReservationByUserId(@PathVariable("id") Long id) {
            log.info("Called getReservationById with id " + id);
            return ResponseEntity.ok(ApiResponse.responseOk(reservationService.getReservationById(id), "Reservation found successfully."));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Reservation>>> getAllReservations(
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
        return ResponseEntity.ok(ApiResponse.responseOk(reservationService.searchAllByFilter(filter),
                "Reservations found successfully."));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<ReservationStats>> getReservationStats(){
        log.info("Called getReservationStats");
        return ResponseEntity.ok(ApiResponse.responseOk(reservationService.getCountStats(), "Reservation stats found successfully."));
    }

    @GetMapping("/groupby")
    public ResponseEntity<ApiResponse<List<Reservation>>> groupByUserIdAndStatus(
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
        return ResponseEntity.ok(ApiResponse.responseOk(reservationService.groupByStatusAndUserId(filter),
                "Reservations found successfully."));
    }


    @PostMapping
    public ResponseEntity<ApiResponse<Reservation>> createReservation(@RequestBody @Valid Reservation reservationToCreate){
        log.info("Called createReservation");
        return ResponseEntity.ok(ApiResponse
                .responseOk(reservationService.createReservation(reservationToCreate),
                        "Reservation created successfully."));
    };

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Reservation>> updateReservation(@PathVariable("id") Long id, @RequestBody Reservation reservationToUpdate){
        log.info("Called updateReservation id={}, reservationToUpdate={}", id, reservationToUpdate);
        var updated = reservationService.updateReservation(id, reservationToUpdate);
        return ResponseEntity.ok(ApiResponse.responseOk(updated,
                "Reservation updated successfully"));
    }

    @PostMapping("/{id}/approve")
    public CompletableFuture<ResponseEntity<ApiResponse<Reservation>>> approveReservation(@PathVariable("id") Long id){
        log.info("Called approveReservation id={}", id);
        return reservationService.approveReservation(id)
                .thenApply(reservation -> ResponseEntity.ok(
                        ApiResponse.responseOk(reservation, "Reservation approved successfully.")
                ));
    }

    @DeleteMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelReservation(@PathVariable("id") Long id){
        log.info("Called cancelReservation id={}", id);
            reservationService.cancelReservation(id);
            return ResponseEntity.ok(ApiResponse.responseOk("Reservation deleted successfully."));
    }
}
