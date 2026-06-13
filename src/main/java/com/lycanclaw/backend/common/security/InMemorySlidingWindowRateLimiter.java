package com.lycanclaw.backend.common.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存滑动窗口限流器。
 * 用于按 key 执行分钟级请求频率控制。
 * @author Wreckloud
 * @since 2026-05-15
 */
@Component
public class InMemorySlidingWindowRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;
    private static final long CLEANUP_INTERVAL = 1024L;

    private final Map<String, ArrayDeque<Long>> buckets = new ConcurrentHashMap<>();
    private final AtomicLong requestCount = new AtomicLong();

    /**
     * 60 秒窗口内请求次数 <= limitPerMinute 时放行。
     */
    public boolean allow(String bucketKey, int limitPerMinute) {
        long now = Instant.now().toEpochMilli();
        long windowStart = now - WINDOW_MILLIS;
        if (requestCount.incrementAndGet() % CLEANUP_INTERVAL == 0) {
            cleanupExpiredBuckets(windowStart);
        }

        int safeLimit = Math.max(1, limitPerMinute);
        ArrayDeque<Long> bucket = buckets.computeIfAbsent(bucketKey, ignored -> new ArrayDeque<>());
        synchronized (bucket) {
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

    private void cleanupExpiredBuckets(long windowStart) {
        buckets.forEach((key, bucket) -> {
            synchronized (bucket) {
                while (!bucket.isEmpty() && bucket.peekFirst() < windowStart) {
                    bucket.pollFirst();
                }
                if (bucket.isEmpty()) {
                    buckets.remove(key, bucket);
                }
            }
        });
    }
}
