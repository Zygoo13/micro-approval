package com.microapproval.api.dto;

import com.microapproval.api.entity.AnalysisMode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSharedReviewSessionRequest(
        @NotBlank(message = "Tiêu đề không được để trống")
        @Size(max = 255, message = "Tiêu đề tối đa 255 ký tự")
        String title,

        @NotNull(message = "Mode phân tích là bắt buộc")
        AnalysisMode mode,

        @NotBlank(message = "Nội dung cần phân tích không được để trống")
        @Size(max = 1_000_000, message = "Nội dung phân tích tối đa 1.000.000 ký tự")
        String rawContent,

        @Size(max = 65_535, message = "Intent tối đa 65.535 ký tự")
        String promptContent
) {
    public CreateSharedReviewSessionRequest {
        if (title != null) {
            title = title.trim();
        }
        if (promptContent != null) {
            promptContent = promptContent.trim();
            if (promptContent.isEmpty()) {
                promptContent = null;
            }
        }
    }

    @AssertTrue(message = "Intent chỉ bắt buộc và được phép khi mode là INTENT_MATCHING")
    public boolean isModeContentValid() {
        if (mode == null) {
            return true;
        }
        return mode == AnalysisMode.INTENT_MATCHING
                ? promptContent != null
                : promptContent == null;
    }
}
