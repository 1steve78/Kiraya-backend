package com.hyperlocal.dispatch.dto;

import java.util.List;

public class DispatchDecision {
    private Long selectedPartnerId;
    private List<DispatchCandidateScore> candidates;
    private String reason;

    public DispatchDecision() {}

    public DispatchDecision(Long selectedPartnerId, List<DispatchCandidateScore> candidates, String reason) {
        this.selectedPartnerId = selectedPartnerId;
        this.candidates = candidates;
        this.reason = reason;
    }

    public Long getSelectedPartnerId() {
        return selectedPartnerId;
    }

    public void setSelectedPartnerId(Long selectedPartnerId) {
        this.selectedPartnerId = selectedPartnerId;
    }

    public List<DispatchCandidateScore> getCandidates() {
        return candidates;
    }

    public void setCandidates(List<DispatchCandidateScore> candidates) {
        this.candidates = candidates;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
