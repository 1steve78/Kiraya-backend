package com.hyperlocal.dispatch.service;

import com.hyperlocal.common.model.Location;
import com.hyperlocal.dispatch.model.DistanceResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CachedDistanceServiceTest {

    @Mock
    private DistanceService delegate;

    private CachedDistanceService cachedDistanceService;

    @BeforeEach
    void setUp() {
        cachedDistanceService = new CachedDistanceService(delegate);
    }

    @Test
    @DisplayName("Should call delegate on cache miss and return cached result on second call")
    void testCacheMissAndHit() {
        Location origin = new Location(12.9716, 77.5946);
        Location destination = new Location(12.9352, 77.6245);

        DistanceResult expectedResult = new DistanceResult(5.2, 16.0);
        when(delegate.getRoute(any(Location.class), any(Location.class))).thenReturn(expectedResult);

        // First call - Cache MISS
        DistanceResult result1 = cachedDistanceService.getRoute(origin, destination);
        assertEquals(5.2, result1.getDistanceKm());
        assertEquals(16.0, result1.getDurationMinutes());
        verify(delegate, times(1)).getRoute(any(Location.class), any(Location.class));

        // Second call with same coordinates - Cache HIT (delegate should NOT be called again)
        DistanceResult result2 = cachedDistanceService.getRoute(origin, destination);
        assertEquals(5.2, result2.getDistanceKm());
        assertEquals(16.0, result2.getDurationMinutes());
        verify(delegate, times(1)).getRoute(any(Location.class), any(Location.class));
    }
}
