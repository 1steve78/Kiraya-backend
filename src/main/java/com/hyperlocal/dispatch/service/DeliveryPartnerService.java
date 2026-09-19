package com.hyperlocal.dispatch.service;

import com.hyperlocal.dispatch.dto.PartnerPresence;
import com.hyperlocal.dispatch.enums.AvailabilityStatus;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Map;

@Service
public class DeliveryPartnerService {

    private final PresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    public DeliveryPartnerService(PresenceService presenceService , SimpMessagingTemplate messagingTemplate){
        this.presenceService = presenceService;
        this.messagingTemplate = messagingTemplate;
    }

    public PartnerPresence goOnline(Long partnerId){
        PartnerPresence current = getCurrentPresence(partnerId);
        if(current.status() == AvailabilityStatus.BUSY){
            throw new IllegalStateException("Cannot switch to ONLINE while currently assigned to an active delivery.");
        }
        presenceService.setOnline(partnerId);
        broadcastPresenceEvent(partnerId,"DELIVERY_PARTNER_ONLINE");
        return new PartnerPresence(partnerId,AvailabilityStatus.ONLINE,Instant.now());
    }

    public PartnerPresence goOffline(Long partnerId) {
        PartnerPresence current = getCurrentPresence(partnerId);
        if (current.status() == AvailabilityStatus.BUSY) {
            throw new IllegalStateException("Cannot go OFFLINE while currently handling a delivery.");
        }

        presenceService.setOffline(partnerId);
        broadcastPresenceEvent(partnerId, "DELIVERY_PARTNER_OFFLINE");
        return new PartnerPresence(partnerId, AvailabilityStatus.OFFLINE, Instant.now());
    }

    public boolean recordHeartbeat(Long partnerId){
        return presenceService.heartBeat(partnerId);
    }

    public PartnerPresence markBusy (Long partnerId){
        PartnerPresence current = getCurrentPresence(partnerId);
        if(current.status() != AvailabilityStatus.ONLINE){
            throw new IllegalStateException("Partner must be ONLINE to accept a job.");
        }
        presenceService.setBusy(partnerId);
        return new PartnerPresence(partnerId,AvailabilityStatus.BUSY , Instant.now());
    }

    public PartnerPresence markJobDelivered(Long partnerId){
        PartnerPresence current = getCurrentPresence(partnerId);
        if(current.status() != AvailabilityStatus.BUSY){
            throw new IllegalStateException("Partner was not in BUSY status.");
        }
        presenceService.setOnline(partnerId);
        return new PartnerPresence(partnerId,AvailabilityStatus.ONLINE,Instant.now());
    }

    public PartnerPresence getCurrentPresence(Long partnerId) {
        return presenceService.getPresence(partnerId)
                .orElse(new PartnerPresence(partnerId, AvailabilityStatus.OFFLINE, Instant.EPOCH));
    }

    public void broadcastPresenceEvent(Long partnerId, String eventType){

        messagingTemplate.convertAndSend("/topic/ops/presence", Map.of(
                "partnerId" , partnerId,
                "event",eventType,
                "timestamp", Instant.now().toString()
        ));
    }
}
