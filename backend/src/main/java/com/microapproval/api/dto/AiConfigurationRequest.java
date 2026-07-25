package com.microapproval.api.dto;

import com.microapproval.api.entity.AiProviderType;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class AiConfigurationRequest {
    @NotNull private AiProviderType provider;
    @NotBlank @Pattern(regexp = "[A-Za-z0-9._:-]{1,100}") private String model;
    @Size(max = 512) private String apiKey;
    @NotNull private Boolean enabled;
}
