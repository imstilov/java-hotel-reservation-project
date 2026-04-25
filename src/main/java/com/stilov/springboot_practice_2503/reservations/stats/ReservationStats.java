package com.stilov.springboot_practice_2503.reservations.stats;

public class ReservationStats {
    public long requestCount; // thread-safe request counter. works only during application life-cycle
    public long monthlyCount;

    public ReservationStats(long requestCount, long monthlyCount) {
        this.requestCount = requestCount;
        this.monthlyCount = monthlyCount;
    }

    public void setRequestCount(long requestCount) {
        this.requestCount = requestCount;
    }

    public void setMonthlyCount(long monthlyCount) {
        this.monthlyCount = monthlyCount;
    }

    public long getRequestCount() {
        return requestCount;
    }

    public long getMonthlyCount() {
        return monthlyCount;
    }
}
