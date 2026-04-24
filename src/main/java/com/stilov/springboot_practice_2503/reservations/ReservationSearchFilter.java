package com.stilov.springboot_practice_2503.reservations;

import org.springframework.web.bind.annotation.RequestParam;

public record ReservationSearchFilter(
        Long roomId,
        Long userId,
        Integer pageSize,
        Integer pageNumber
) {
}
