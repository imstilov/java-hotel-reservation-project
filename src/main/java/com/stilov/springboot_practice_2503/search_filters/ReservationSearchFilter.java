package com.stilov.springboot_practice_2503.search_filters;

public record ReservationSearchFilter(
        Long roomId,
        Long userId,
        Integer pageSize,
        Integer pageNumber
) {
}
