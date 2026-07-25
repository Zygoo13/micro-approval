package com.microapproval.api.dto;

import com.microapproval.api.entity.DecisionStatus;
import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Data
public class DecisionVoteRequest {
    // Giá trị truyền lên phải là APPROVED hoặc REJECTED
    @NotNull(message = "Quyết định là bắt buộc")
    private DecisionStatus humanDecision;

    // Ghi chú của người kiểm duyệt (có thể null)
    @Size(max = 65535, message = "Ghi chú quá dài")
    private String reviewerNote;
}
