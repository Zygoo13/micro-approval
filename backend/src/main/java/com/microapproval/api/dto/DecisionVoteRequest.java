package com.microapproval.api.dto;

import com.microapproval.api.entity.DecisionStatus;
import lombok.Data;

@Data
public class DecisionVoteRequest {
    // Giá trị truyền lên phải là APPROVED hoặc REJECTED
    private DecisionStatus humanDecision;

    // Ghi chú của người kiểm duyệt (có thể null)
    private String reviewerNote;
}