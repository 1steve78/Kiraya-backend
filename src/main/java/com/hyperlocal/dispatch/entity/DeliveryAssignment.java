package com.hyperlocal.dispatch.entity;

import com.hyperlocal.dispatch.enums.DeliveryAssignmentStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "delivery_assignments", indexes = {
        @Index(name = "idx_assignment_delivery_id", columnList = "delivery_id")
})
public class DeliveryAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "delivery_id", nullable = false)
    private Long deliveryId;

    @Column(name = "candidate_ids")
    private String candidateIds;

    @Column(name = "current_candidate_index", nullable = false)
    private int currentCandidateIndex = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryAssignmentStatus status = DeliveryAssignmentStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public DeliveryAssignment() {
    }

    public DeliveryAssignment(Long deliveryId, List<Long> candidateIds) {
        this.deliveryId = deliveryId;
        setCandidateIds(candidateIds);
        this.currentCandidateIndex = 0;
        this.status = DeliveryAssignmentStatus.IN_PROGRESS;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public List<Long> getCandidateIds() {
        if (candidateIds == null || candidateIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(candidateIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .toList();
    }

    public void setCandidateIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            this.candidateIds = "";
        } else {
            this.candidateIds = ids.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
        }
    }

    public Long getCurrentCandidateId() {
        List<Long> ids = getCandidateIds();
        if (currentCandidateIndex >= 0 && currentCandidateIndex < ids.size()) {
            return ids.get(currentCandidateIndex);
        }
        return null;
    }

    public boolean hasNextCandidate() {
        return currentCandidateIndex + 1 < getCandidateIds().size();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Long getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(Long deliveryId) {
        this.deliveryId = deliveryId;
    }

    public int getCurrentCandidateIndex() {
        return currentCandidateIndex;
    }

    public void setCurrentCandidateIndex(int currentCandidateIndex) {
        this.currentCandidateIndex = currentCandidateIndex;
    }

    public DeliveryAssignmentStatus getStatus() {
        return status;
    }

    public void setStatus(DeliveryAssignmentStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
