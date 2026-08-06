package com.microapproval.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssignSessionReviewerRequest(
        @NotBlank(message = "Workspace member là bắt buộc")
        @Size(max = 36, message = "Workspace member ID không hợp lệ")
        String workspaceMemberId
) {
    public AssignSessionReviewerRequest {
        if (workspaceMemberId != null) {
            workspaceMemberId = workspaceMemberId.trim();
        }
    }
}
