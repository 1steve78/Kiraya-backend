package com.hyperlocal.dispatch.controller;

import com.hyperlocal.dispatch.dto.DeliveryPartnerCandidate;
import com.hyperlocal.dispatch.enums.RejectionReason;
import com.hyperlocal.dispatch.service.EligiblePartnerService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/dispatch")
public class DispatchController {

    private final EligiblePartnerService eligiblePartnerService;

    public DispatchController(EligiblePartnerService eligiblePartnerService) {
        this.eligiblePartnerService = eligiblePartnerService;
    }

    @GetMapping("/candidates")
    public ResponseEntity<List<DeliveryPartnerCandidate>> getCandidates(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(required = false) Double radius) {

        List<DeliveryPartnerCandidate> candidates;
        if (radius != null) {
            candidates = eligiblePartnerService.findEligiblePartners(latitude, longitude, radius);
        } else {
            candidates = eligiblePartnerService.findEligiblePartners(latitude, longitude);
        }

        return ResponseEntity.ok(candidates);
    }
    
    @GetMapping("/candidates/debug")
    public ResponseEntity<String> getCandidatesDebug(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(required = false) Double radius) {

        List<DeliveryPartnerCandidate> candidates;
        if (radius != null) {
            candidates = eligiblePartnerService.findEligiblePartners(latitude, longitude, radius);
        } else {
            candidates = eligiblePartnerService.findEligiblePartners(latitude, longitude);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("              \uD83D\uDCCD PICKUP\n");
        sb.append("                 ⭐\n\n");

        List<DeliveryPartnerCandidate> eligible = candidates.stream().filter(DeliveryPartnerCandidate::isEligible).toList();
        List<DeliveryPartnerCandidate> rejected = candidates.stream().filter(c -> !c.isEligible()).toList();

        for (DeliveryPartnerCandidate c : candidates) {
            String emoji = c.isEligible() ? "\uD83D\uDFE2" : (c.getReason() == RejectionReason.BUSY ? "\uD83D\uDFE0" : "\uD83D\uDD34");
            String statusOrReason = c.isEligible() ? "" : (c.getReason() != null ? c.getReason().name() : "");
            sb.append(String.format("       \uD83D\uDE9A Partner %d %.2fkm   %s %s\n", c.getPartnerId(), c.getDistanceKm(), emoji, statusOrReason));
        }

        sb.append("\n──────────────────────────────\n\n");
        sb.append("Eligible:\n");
        for (DeliveryPartnerCandidate c : eligible) {
            sb.append(String.format("\uD83D\uDFE2 Partner %d\n", c.getPartnerId()));
        }

        sb.append("\nRejected:\n");
        for (DeliveryPartnerCandidate c : rejected) {
            String emoji = c.getReason() == RejectionReason.BUSY ? "\uD83D\uDFE0" : "\uD83D\uDD34";
            String reasonStr = c.getReason() != null ? c.getReason().name() : "";
            sb.append(String.format("%s Partner %d → %s\n", emoji, c.getPartnerId(), reasonStr));
        }

        return ResponseEntity.ok(sb.toString());
    }
}
