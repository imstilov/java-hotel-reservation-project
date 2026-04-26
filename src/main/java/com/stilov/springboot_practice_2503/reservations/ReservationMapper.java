package com.stilov.springboot_practice_2503.reservations;

import org.springframework.stereotype.Component;

@Component
public class ReservationMapper {

    public ReservationDTO toDomain(ReservationEntity reservation) {
        return new ReservationDTO(
                reservation.getId(),
                reservation.getUserId(),
                reservation.getRoomId(),
                reservation.getStartDate(),
                reservation.getEndDate(),
                reservation.getStatus()
        );
    }


    public ReservationEntity toEntity(ReservationDTO reservationDTO) {
        return new ReservationEntity(
                reservationDTO.id(),
                reservationDTO.userId(),
                reservationDTO.roomId(),
                reservationDTO.startDate(),
                reservationDTO.endDate(),
                reservationDTO.status()
        );
    }

}
