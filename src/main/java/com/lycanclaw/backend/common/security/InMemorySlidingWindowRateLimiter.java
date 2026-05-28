package com.lycanclaw.backend.common.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存滑动窗口限流器
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Component
public class InMemorySlidingWindowRateLimiter {

    private final Map<String, ArrayDeque<Long>> buckets = new ConcurrentHashMap<>();

    /**
     * 60 秒窗口内请求次数 <= limitPerMinute 时放行。
     */
    public boolean allow(String bucketKey, int limitPerMinute) {
        long now = Instant.now().toEpochMilli();
        int safeLimit = Math.max(1, limitPerMinute);
        ArrayDeque<Long> bucket = buckets.computeIfAbsent(bucketKey, ignored -> new ArrayDeque<>());
        synchronized (bucket) {
            long windowStart = now - 60_000L;
            while (!bucket.isEmpty() && bucket.peekFirst() < windowStart) {
                bucket.pollFirst();
            }
            if (bucket.size() >= safeLimit) {
                return false;
            }
            bucket.addLast(now);
            return true;
        }
    }
}
