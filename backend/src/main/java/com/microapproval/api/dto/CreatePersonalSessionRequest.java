package com.microapproval.api.dto;

import com.microapproval.api.entity.AnalysisMode;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Data
public class CreatePersonalSessionRequest {
    @NotBlank(message = "Tiêu đề không được để trống")
    @Size(max = 255, message = "Tiêu đề tối đa 255 ký tự")
    private String title;

    @NotNull(message = "Mode phân tích là bắt buộc")
    private AnalysisMode mode;

    @NotBlank(message = "Nội dung cần phân tích không được để trống")
    private String rawContent;

    @Size(max = 65535, message = "Prompt quá dài")
    private String promptContent;
}
