package com.stilov.springboot_practice_2503.ratelimit;

import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@AllArgsConstructor
public class FixedWindowRateLimiter {

    private final StringRedisTemplate stringRedisTemplate;

    public boolean allowRequest(
            String clientId,
            int limit,
            Duration windowSize
    ){
        long windowIndex = System.currentTimeMillis() / windowSize.toMillis();
        String key = String.format("rate:%s:%s", clientId, windowIndex);

        Long countHits = stringRedisTemplate.opsForValue()
                .increment(key);

        if(countHits != null && countHits == 1){
            stringRedisTemplate.expire(key, windowSize);
        }

        return countHits != null && countHits <= limit;
    }
}
