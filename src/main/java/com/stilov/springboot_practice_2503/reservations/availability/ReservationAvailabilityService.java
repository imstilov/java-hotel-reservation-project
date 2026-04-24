package com.stilov.springboot_practice_2503.reservations.availability;

import com.stilov.springboot_practice_2503.reservations.ReservationRepository;
import com.stilov.springboot_practice_2503.reservations.ReservationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class ReservationAvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(ReservationAvailabilityController.class);

    private ReservationRepository reservationRepository;
    @Autowired
    public ReservationAvailabilityService(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }


    public boolean isReservationAvailable(
            Long roomId,
            LocalDate startDate,
            LocalDate endDate) {

        if(!endDate.isAfter(startDate)){
            throw new IllegalArgumentException("Reservation start date must be after end date");
        }
        List<Long> conflictingIds = reservationRepository.findConflictReservartionsIds(roomId,
                startDate,
                endDate,
                ReservationStatus.APPROVED);
        if(conflictingIds.isEmpty()){
            return true;
        }
        log.info("Conflict with ids = {}", conflictingIds);
        return false;
    }
}
