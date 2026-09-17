package com.hyperlocal.service;

import com.hyperlocal.dto.NearbyPartnerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.data.redis.domain.geo.GeoShape;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class GeoLocationService {

    private static final Logger log = LoggerFactory.getLogger(GeoLocationService.class);
    private static final String GEO_KEY = "delivery:partners:geo";

    private final StringRedisTemplate redisTemplate;

    public GeoLocationService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void addPartnerLocation(Long partnerId, double latitude, double longitude) {
        log.debug("Adding partner {} to geo index at lat={}, lng={}", partnerId, latitude, longitude);
        redisTemplate.opsForGeo().add(GEO_KEY, new Point(longitude, latitude), "partner:" + partnerId);
    }

    public void updatePartnerLocation(Long partnerId, double latitude, double longitude) {
        // GEOADD also updates existing members
        addPartnerLocation(partnerId, latitude, longitude);
    }

    public void removePartner(Long partnerId) {
        log.debug("Removing partner {} from geo index", partnerId);
        redisTemplate.opsForGeo().remove(GEO_KEY, "partner:" + partnerId);
    }

    public void removePartners(List<String> partnerIds) {
        if (partnerIds == null || partnerIds.isEmpty()) return;
        String[] members = partnerIds.stream().map(id -> "partner:" + id).toArray(String[]::new);
        redisTemplate.opsForGeo().remove(GEO_KEY, members);
    }

    public List<NearbyPartnerResponse> findNearbyPartners(double latitude, double longitude, double radiusKm) {
        log.debug("Searching for nearby partners lat={}, lng={}, radius={}km", latitude, longitude, radiusKm);
        
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo().search(
                GEO_KEY,
                GeoReference.fromCoordinate(new Point(longitude, latitude)),
                GeoShape.byRadius(new Distance(radiusKm, Metrics.KILOMETERS)),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().sortAscending()
        );

        List<NearbyPartnerResponse> nearbyPartners = new ArrayList<>();
        if (results != null) {
            for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : results) {
                String member = result.getContent().getName();
                Long partnerId = Long.parseLong(member.replace("partner:", ""));
                Double distance = result.getDistance().getValue();
                nearbyPartners.add(new NearbyPartnerResponse(partnerId, distance));
            }
        }
        return nearbyPartners;
    }
}
