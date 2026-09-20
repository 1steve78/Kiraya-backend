package com.hyperlocal.dispatch.dto;

public class DispatchCandidateScore {
    private Long partnerId;
    private Double distanceKm;
    private Long locationFreshness;
    private Integer estimatedEta; // in minutes, or just null for now
    private Double score;
    private Integer activeWorkload;

    public DispatchCandidateScore() {}

    public DispatchCandidateScore(Long partnerId, Double distanceKm, Long locationFreshness, Integer estimatedEta, Double score, Integer activeWorkload) {
        this.partnerId = partnerId;
        this.distanceKm = distanceKm;
        this.locationFreshness = locationFreshness;
        this.estimatedEta = estimatedEta;
        this.score = score;
        this.activeWorkload = activeWorkload;
    }

    public Long getPartnerId() { return partnerId; }
    public void setPartnerId(Long partnerId) { this.partnerId = partnerId; }
    
    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }
    
    public Long getLocationFreshness() { return locationFreshness; }
    public void setLocationFreshness(Long locationFreshness) { this.locationFreshness = locationFreshness; }
    
    public Integer getEstimatedEta() { return estimatedEta; }
    public void setEstimatedEta(Integer estimatedEta) { this.estimatedEta = estimatedEta; }
    
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    
    public Integer getActiveWorkload() { return activeWorkload; }
    public void setActiveWorkload(Integer activeWorkload) { this.activeWorkload = activeWorkload; }
}
