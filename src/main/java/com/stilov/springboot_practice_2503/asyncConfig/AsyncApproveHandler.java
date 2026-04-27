package com.stilov.springboot_practice_2503.asyncConfig;

import com.stilov.springboot_practice_2503.reservations.ReservationDTO;
import com.stilov.springboot_practice_2503.reservations.ReservationService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class AsyncApproveHandler {

    private final ReservationService reservationService;

    public AsyncApproveHandler(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Async
    public CompletableFuture<ReservationDTO> approveReservationAsync(Long id){
        ReservationDTO newDTO = reservationService.approveReservation(id);

        return CompletableFuture.completedFuture(newDTO);
    }
}
