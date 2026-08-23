package com.hyperlocal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hyperlocal.exception.DistanceServiceException;
import com.hyperlocal.model.DistanceResult;
import com.hyperlocal.model.Location;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service("googleDistanceService")
public class GoogleDistanceService implements DistanceService {

    private final RestTemplate restTemplate;
    private final String apiKey;

    private static final String GOOGLE_DISTANCE_URL =
            "https://maps.googleapis.com/maps/api/distancematrix/json?origins={origin}&destinations={dest}&key={key}";

    public GoogleDistanceService(RestTemplate restTemplate,
                                 @Value("${google.maps.api-key}") String apiKey) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
    }

    @Override
    public DistanceResult getRoute(Location origin, Location destination) {
        if (origin == null || destination == null) {
            throw new DistanceServiceException("Origin and Destination locations must not be null");
        }

        // 1. Build the comma-separated strings Google expects
        String originStr = origin.getLatitude() + "," + origin.getLongitude();
        String destStr = destination.getLatitude() + "," + destination.getLongitude();

        try {
            // 2. Make the HTTP Request
            JsonNode response = restTemplate.getForObject(
                    GOOGLE_DISTANCE_URL,
                    JsonNode.class,
                    originStr,
                    destStr,
                    apiKey
            );

            // 3. Parse the Response
            return parseGoogleResponse(response);

        } catch (RestClientException e) {
            // 4. Translate framework exceptions into our domain exception
            throw new DistanceServiceException("Failed to call Google Distance Matrix API", e);
        }
    }

    private DistanceResult parseGoogleResponse(JsonNode response) {
        if (response == null || !"OK".equals(response.path("status").asText())) {
            String errorMsg = (response != null && response.has("error_message"))
                    ? response.path("error_message").asText()
                    : "Google API returned invalid status or empty response.";
            throw new DistanceServiceException("Google API error: " + errorMsg);
        }

        JsonNode rows = response.path("rows");
        if (rows.isMissingNode() || !rows.isArray() || rows.isEmpty()) {
            throw new DistanceServiceException("Google API returned no rows");
        }

        JsonNode elements = rows.get(0).path("elements");
        if (elements.isMissingNode() || !elements.isArray() || elements.isEmpty()) {
            throw new DistanceServiceException("Google API returned no elements");
        }

        JsonNode element = elements.get(0);
        if (!"OK".equals(element.path("status").asText())) {
            throw new DistanceServiceException("No route found between locations.");
        }

        // Google returns distance in meters and duration in seconds
        double distanceMeters = element.path("distance").path("value").asDouble();
        double durationSeconds = element.path("duration").path("value").asDouble();

        // 5. Normalize to our internal standard (Kilometers and Minutes)
        double distanceKm = distanceMeters / 1000.0;
        double durationMinutes = durationSeconds / 60.0;

        return new DistanceResult(distanceKm, durationMinutes);
    }
}
