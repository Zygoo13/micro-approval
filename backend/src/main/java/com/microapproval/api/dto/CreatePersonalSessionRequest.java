package com.microapproval.api.dto;

import com.microapproval.api.entity.AnalysisMode;
import lombok.Data;

@Data
public class CreatePersonalSessionRequest {
    private String title;

    private AnalysisMode mode; // Thay vì String

    private String rawContent;

    private String promptContent;
}