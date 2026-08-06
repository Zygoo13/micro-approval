package com.microapproval.api.dto;

public record SessionAuditChangeResponse(
        SessionAuditValueResponse oldValue,
        SessionAuditValueResponse newValue
) {
}
