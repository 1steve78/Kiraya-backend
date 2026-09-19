package com.hyperlocal.dispatch.model;

public class RouteCacheEntry {
    private final DistanceResult result;
    private final long timestamp;

    public RouteCacheEntry(DistanceResult result) {
        this.result = result;
        this.timestamp = System.currentTimeMillis();
    }

    public DistanceResult getResult() {
        return result;
    }

    // Check if the cache is older than the allowed TTL (Time To Live)
    public boolean isExpired(long ttlMillis) {
        return System.currentTimeMillis() - timestamp > ttlMillis;
    }
}
