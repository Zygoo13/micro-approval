package com.microapproval.api.dto;

import com.microapproval.api.entity.TeamVoteDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpsertTeamVoteRequest(
        @NotNull(message = "Quyết định vote là bắt buộc")
        TeamVoteDecision decision,
        @Size(max = 2000, message = "Ghi chú tối đa 2.000 ký tự")
        String note,
        @PositiveOrZero(message = "Vote version không hợp lệ")
        Long version
) {
    public UpsertTeamVoteRequest {
        if (note != null) {
            note = note.trim();
            if (note.isEmpty()) {
                note = null;
            }
        }
    }
}
