package com.stilov.springboot_practice_2503.reservations;

import com.stilov.springboot_practice_2503.asyncConfig.AsyncApproveHandler;
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

import static org.springframework.data.jpa.domain.AbstractPersistable_.id;

@RestController
@RequestMapping("/reservation")
public class ReservationController {

    private static final Logger log = LoggerFactory.getLogger(ReservationController.class);

    private final ReservationService reservationService;

    private final ManualCachingReservationService manualCachingReservationService;

    private final AsyncApproveHandler asyncApproveHandler;

    public ReservationController(ReservationService reservationService, ManualCachingReservationService manualCachingReservationService, AsyncApproveHandler asyncApproveHandler) {
        this.reservationService = reservationService;
        this.manualCachingReservationService = manualCachingReservationService;
        this.asyncApproveHandler = asyncApproveHandler;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReservationDTO>> getReservationByUserId(
            @PathVariable("id") Long id,
            @RequestParam(value = "cacheMode", defaultValue = "NONE_CACHE") CacheMode cacheMode) {
            log.info("Called getReservationById with id={} , with cache mode = {} ",id, cacheMode);

            ReservationServiceInteface service = resolveReservationService(cacheMode);
            return ResponseEntity
                    .ok(ApiResponse.responseOk(service.getReservationById(id),
                            "Reservation found successfully."));
    }

    private ReservationServiceInteface resolveReservationService(CacheMode cacheMode){
        return switch(cacheMode){
            case NONE_CACHE -> reservationService;
            case MANUAL -> manualCachingReservationService;
        };
    }

    @GetMapping("/themostpopular/top3")
    public ResponseEntity<ApiResponse<List<Long>>> getTop3Reservations(){
        log.info("Called getTop3Reservations controller method");
        return ResponseEntity.ok(ApiResponse.responseOk(reservationService.getMostPopularRooms(), "Reservations were found successfully."));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ReservationDTO>>> getAllReservations(
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
    public ResponseEntity<ApiResponse<List<ReservationDTO>>> groupByUserIdAndStatus(
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
    public ResponseEntity<ApiResponse<ReservationDTO>> createReservation(@RequestBody @Valid ReservationDTO reservationDTOToCreate){
        log.info("Called createReservation");
        return ResponseEntity.ok(ApiResponse
                .responseOk(reservationService.createReservation(reservationDTOToCreate),
                        "Reservation created successfully."));
    };

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ReservationDTO>> updateReservation(@PathVariable("id") Long id, @RequestBody ReservationDTO reservationDTOToUpdate){
        log.info("Called updateReservation id={}, reservationToUpdate={}", id, reservationDTOToUpdate);
        var updated = reservationService.updateReservation(id, reservationDTOToUpdate);
        return ResponseEntity.ok(ApiResponse.responseOk(updated,
                "Reservation updated successfully"));
    }

    @PostMapping("/{id}/approve")
    public CompletableFuture<ResponseEntity<ApiResponse<ReservationDTO>>> approveReservation(@PathVariable("id") Long id){
        log.info("Called approveReservation id={}", id);
        return asyncApproveHandler.approveReservationAsync(id)
                .thenApply(reservationDTO -> ResponseEntity.ok(
                        ApiResponse.responseOk(reservationDTO, "Reservation approved successfully.")
                ));
    }

    @DeleteMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelReservation(@PathVariable("id") Long id){
        log.info("Called cancelReservation id={}", id);
            reservationService.cancelReservation(id);
            return ResponseEntity.ok(ApiResponse.responseOk("Reservation deleted successfully."));
    }
}
