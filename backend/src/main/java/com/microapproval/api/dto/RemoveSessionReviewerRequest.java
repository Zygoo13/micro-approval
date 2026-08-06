package com.microapproval.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RemoveSessionReviewerRequest(
        @NotBlank(message = "Lý do remove reviewer là bắt buộc")
        @Size(max = 1000, message = "Lý do tối đa 1.000 ký tự")
        String reason
) {
    public RemoveSessionReviewerRequest {
        if (reason != null) {
            reason = reason.trim();
        }
    }
}
