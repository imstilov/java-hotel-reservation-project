package com.stilov.springboot_practice_2503;

import com.stilov.springboot_practice_2503.reservations.ReservationEntity;
import com.stilov.springboot_practice_2503.reservations.ReservationRepository;
import com.stilov.springboot_practice_2503.reservations.ReservationService;
import com.stilov.springboot_practice_2503.reservations.ReservationStatus;
import com.stilov.springboot_practice_2503.reservations.availability.ReservationAvailabilityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
class ReservationServiceConcurrencyTest {

    @Autowired private ReservationService service;
    @Autowired private ReservationRepository repo;
    @MockitoSpyBean private ReservationAvailabilityService availabilityService;

    private static final Long TEST_ROOM_ID = 9999L;
    private static final Long TEST_USER_ID = 9999L;

    private final List<Long> createdIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        createdIds.forEach(id -> repo.deleteById(id));
        createdIds.clear();
    }

    private Long createPendingReservation(String checkIn, String checkOut) {
        ReservationEntity r = new ReservationEntity();
        r.setRoomId(TEST_ROOM_ID);
        r.setUserId(TEST_USER_ID);
        r.setStartDate(LocalDate.parse(checkIn));
        r.setEndDate(LocalDate.parse(checkOut));
        r.setStatus(ReservationStatus.PENDING);
        Long id = repo.save(r).getId();
        createdIds.add(id);
        return id;
    }

    @Test
    void shouldAllowOnlyOneApprovalForOverlappingReservations() throws InterruptedException {
        Long id1 = createPendingReservation("2026-05-10", "2026-05-15");
        Long id2 = createPendingReservation("2026-05-12", "2026-05-14");

        CyclicBarrier barrier = new CyclicBarrier(2);

        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            try {
                barrier.await(5, TimeUnit.SECONDS);
            } catch (BrokenBarrierException e) {
                throw new RuntimeException(e);
            }
            return result;
        }).when(availabilityService).isReservationAvailable(anyLong(), any(), any());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger successCount = new AtomicInteger();

        executor.submit(() -> {
            try {
                service.approveReservation(id1);
                successCount.incrementAndGet();
            } catch (Exception ignored) {}
        });

        executor.submit(() -> {
            try {
                service.approveReservation(id2);
                successCount.incrementAndGet();
            } catch (Exception ignored) {}
        });

        executor.shutdown();
        executor.awaitTermination(15, TimeUnit.SECONDS);

        // 0 is valid: SERIALIZABLE aborted both due to symmetric conflict (retry logic will fix this)
        // 2 is invalid: ACID violation — both overlapping reservations approved simultaneously
        assertTrue(successCount.get() < 2,
                "ACID violation: both overlapping reservations were approved simultaneously");
    }
}
