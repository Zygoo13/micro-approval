package com.microapproval.api.dto;

import com.microapproval.api.entity.AiProviderType;
import java.time.LocalDateTime;

public record AiConfigurationResponse(boolean configured, AiProviderType provider, String model, boolean enabled, String keySuffix, LocalDateTime updatedAt) { }
