package com.stilov.springboot_practice_2503.asyncConfig;

import com.stilov.springboot_practice_2503.reservations.ReservationDTO;
import com.stilov.springboot_practice_2503.reservations.NonCacheReservationService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class AsyncApproveHandler {

    private final NonCacheReservationService nonCacheReservationService;

    public AsyncApproveHandler(NonCacheReservationService nonCacheReservationService) {
        this.nonCacheReservationService = nonCacheReservationService;
    }

    @Async
    public CompletableFuture<ReservationDTO> approveReservationAsync(Long id){
        ReservationDTO newDTO = nonCacheReservationService.approveReservation(id);

        return CompletableFuture.completedFuture(newDTO);
    }
}
