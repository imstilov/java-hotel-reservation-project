package com.stilov.springboot_practice_2503.reservations.stats;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RequestCounterService {
    AtomicInteger counter = new AtomicInteger(0);

    public RequestCounterService() {}

    public void increment(){
        counter.incrementAndGet();
    }

    public int getCount(){
        return counter.get();
    }

}
