package com.vodchyts.backend.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitingService {

    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> generalBuckets = new ConcurrentHashMap<>();

    private Bucket createNewLoginBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(5))))
                .build();
    }

    private Bucket createNewGeneralBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(50, Refill.intervally(50, Duration.ofMinutes(1))))
                .build();
    }

    public Bucket resolveLoginBucket(String ip) {
        return loginBuckets.computeIfAbsent(ip, k -> createNewLoginBucket());
    }

    public Bucket resolveGeneralBucket(String ip) {
        return generalBuckets.computeIfAbsent(ip, k -> createNewGeneralBucket());
    }
}