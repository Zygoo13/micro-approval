package com.microapproval.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

public record CloseSharedReviewSessionRequest(
        @Size(max = 1000, message = "Lý do đóng session tối đa 1.000 ký tự")
        String reason
) {
    public CloseSharedReviewSessionRequest {
        if (reason != null) {
            reason = reason.trim();
        }
    }

    @AssertTrue(message = "Lý do đóng session không được chỉ chứa khoảng trắng")
    public boolean isReasonValid() {
        return reason == null || !reason.isEmpty();
    }
}
