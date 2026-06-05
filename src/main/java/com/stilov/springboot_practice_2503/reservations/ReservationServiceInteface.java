package com.stilov.springboot_practice_2503.reservations;

import com.stilov.springboot_practice_2503.search_filters.GroupByUserAndStatus;
import com.stilov.springboot_practice_2503.search_filters.ReservationSearchFilter;

import java.util.List;

public interface ReservationServiceInteface {
    List<ReservationDTO> searchAllByFilter(ReservationSearchFilter filter);
    List<ReservationDTO> groupByStatusAndUserId(GroupByUserAndStatus filter);
    List<Long> getMostPopularRooms();
    ReservationDTO getReservationById(Long id);
    ReservationDTO createReservation(ReservationDTO reservationDTOToCreate);
    ReservationDTO updateReservation(
            Long id,
            ReservationDTO reservationDTOToUpdate);
    void cancelReservation(Long id);
    ReservationDTO approveReservation(Long id);
}
