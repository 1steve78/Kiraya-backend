package com.hyperlocal.service;

import com.hyperlocal.model.DistanceResult;
import com.hyperlocal.model.Location;
import com.hyperlocal.model.RouteCacheEntry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Primary // 🚨 Tell Spring to inject THIS class into DispatchService
public class CachedDistanceService implements DistanceService {

    // The "real" service we will call if cache misses
    private final DistanceService delegate;

    // Thread-safe map for our in-memory cache
    private final Map<String, RouteCacheEntry> cache = new ConcurrentHashMap<>();

    // Cache Time-To-Live: 5 minutes (in milliseconds)
    private static final long CACHE_TTL_MS = 5 * 60 * 1000;

    // Use @Qualifier to specifically inject the Google implementation
    public CachedDistanceService(@Qualifier("googleDistanceService") DistanceService delegate) {
        this.delegate = delegate;
    }

    @Override
    public DistanceResult getRoute(Location origin, Location destination) {
        if (origin == null || destination == null) {
            return delegate.getRoute(origin, destination);
        }

        // Create a unique key for this specific route (Rounding to roughly 100 meters)
        // Formatting to 3 decimal places ensures minor GPS jitters don't bust the cache
        String cacheKey = generateCacheKey(origin, destination);

        RouteCacheEntry entry = cache.get(cacheKey);

        // 1. Check if we have a valid cache hit
        if (entry != null && !entry.isExpired(CACHE_TTL_MS)) {
            System.out.println("⚡ Cache HIT for route: " + cacheKey);
            return entry.getResult();
        }

        // 2. Cache miss or expired. Call the real Google service
        System.out.println("☁️ Cache MISS. Calling routing API for: " + cacheKey);
        DistanceResult freshResult = delegate.getRoute(origin, destination);

        // 3. Save the new result to the cache
        cache.put(cacheKey, new RouteCacheEntry(freshResult));

        return freshResult;
    }

    private String generateCacheKey(Location origin, Location destination) {
        return String.format("%.3f,%.3f-%.3f,%.3f",
                origin.getLatitude(), origin.getLongitude(),
                destination.getLatitude(), destination.getLongitude());
    }
}
