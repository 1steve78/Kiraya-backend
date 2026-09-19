package com.hyperlocal.dispatch.service;

import com.hyperlocal.common.model.Location;
import com.hyperlocal.dispatch.exception.DistanceServiceException;
import com.hyperlocal.dispatch.model.DistanceResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleDistanceServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private GoogleDistanceService googleDistanceService;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        googleDistanceService = new GoogleDistanceService(restTemplate, "test-api-key");
    }

    @Test
    @DisplayName("Should successfully parse valid Google Distance Matrix API response")
    void testGetRoute_Success() {
        ObjectNode root = mapper.createObjectNode();
        root.put("status", "OK");

        ArrayNode rows = root.putArray("rows");
        ObjectNode row = rows.addObject();
        ArrayNode elements = row.putArray("elements");
        ObjectNode element = elements.addObject();
        element.put("status", "OK");

        ObjectNode distance = element.putObject("distance");
        distance.put("value", 5200); // 5.2 km

        ObjectNode duration = element.putObject("duration");
        duration.put("value", 960); // 16 minutes

        when(restTemplate.getForObject(anyString(), eq(com.fasterxml.jackson.databind.JsonNode.class), any(), any(), any()))
                .thenReturn(root);

        Location origin = new Location(12.9716, 77.5946);
        Location destination = new Location(12.9352, 77.6245);

        DistanceResult result = googleDistanceService.getRoute(origin, destination);

        assertNotNull(result);
        assertEquals(5.2, result.getDistanceKm());
        assertEquals(16.0, result.getDurationMinutes());
    }

    @Test
    @DisplayName("Should throw DistanceServiceException when RestTemplate fails")
    void testGetRoute_RestClientException() {
        when(restTemplate.getForObject(anyString(), eq(com.fasterxml.jackson.databind.JsonNode.class), any(), any(), any()))
                .thenThrow(new RestClientException("Connection refused"));

        Location origin = new Location(12.9716, 77.5946);
        Location destination = new Location(12.9352, 77.6245);

        assertThrows(DistanceServiceException.class,
                () -> googleDistanceService.getRoute(origin, destination));
    }
}
