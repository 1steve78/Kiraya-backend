package com.hyperlocal.dispatch.service;

import com.hyperlocal.dispatch.dto.PartnerPresence;
import com.hyperlocal.dispatch.enums.AvailabilityStatus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class PresenceService {

    private static final  Logger log = LoggerFactory.getLogger(PresenceService.class);
    private static final Duration PRESENCE_TTL = Duration.ofSeconds(30);
    private static final String KEY_PREFIX = "partner:";
    private static final String KEY_SUFFIX = ":presence";
    private static final String AVAILABLE_SET_KEY = "partners:available";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final GeoLocationService geoLocationService;

    public PresenceService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, GeoLocationService geoLocationService) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.geoLocationService = geoLocationService;
    }

    private  String buildKey(Long partnerId){
        return KEY_PREFIX + partnerId + KEY_SUFFIX;
    }

    public void setOnline(Long partnerId){
        savePresence(partnerId,AvailabilityStatus.ONLINE , PRESENCE_TTL);
        redisTemplate.opsForSet().add(AVAILABLE_SET_KEY,partnerId.toString());
    }
    public void setBusy(Long partnerId) {
        savePresence(partnerId, AvailabilityStatus.BUSY, PRESENCE_TTL);
        redisTemplate.opsForSet().remove(AVAILABLE_SET_KEY, partnerId.toString());
    }

    public void setOffline(Long partnerId) {
        redisTemplate.delete(buildKey(partnerId));
        redisTemplate.opsForSet().remove(AVAILABLE_SET_KEY, partnerId.toString());
        geoLocationService.removePartner(partnerId);
    }

    public boolean heartBeat(Long partnerId){
        Optional<PartnerPresence> current = getPresence(partnerId);

        if(current.isEmpty() || current.get().status() == AvailabilityStatus.OFFLINE){
            return false;
        }

        savePresence(partnerId,current.get().status(),PRESENCE_TTL);
        if(current.get().status() == AvailabilityStatus.ONLINE){
            redisTemplate.opsForSet().add(AVAILABLE_SET_KEY,partnerId.toString());
        }
        return true;
    }

    public Optional<PartnerPresence> getPresence(Long partnerId){
        String json = redisTemplate.opsForValue().get(buildKey(partnerId));
        if(json == null){
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, PartnerPresence.class));
        } catch (JsonProcessingException e){
            log.error("Failed to deserialize presence for partner: {}", partnerId, e);
            return Optional.empty();
        }
    }

    public List<PartnerPresence> getAvailablePartners(){
        Set<String> memberIds = redisTemplate.opsForSet().members(AVAILABLE_SET_KEY);
        if(memberIds == null || memberIds.isEmpty()){
            return List.of();
        }

        List<PartnerPresence> onlinePartners = new ArrayList<>();
        List<String> staleIds = new ArrayList<>();

        for(String idStr : memberIds){
            Long id = Long.valueOf(idStr);
            Optional<PartnerPresence> presence = getPresence(id);

            if(presence.isEmpty() || presence.get().status() != AvailabilityStatus.ONLINE){
                staleIds.add(idStr);
            } else{
                onlinePartners.add(presence.get());
            }
        }

        if(!staleIds.isEmpty()){
            redisTemplate.opsForSet().remove(AVAILABLE_SET_KEY,staleIds.toArray());
            // Also remove from Geo index
            geoLocationService.removePartners(staleIds);
        }
        return onlinePartners;
    }

    private void savePresence(Long partnerId, AvailabilityStatus status, Duration ttl) {
        PartnerPresence presence = new PartnerPresence(partnerId, status, Instant.now());
        try {
            String json = objectMapper.writeValueAsString(presence);
            redisTemplate.opsForValue().set(buildKey(partnerId), json, ttl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize partner presence", e);
        }
    }
}
