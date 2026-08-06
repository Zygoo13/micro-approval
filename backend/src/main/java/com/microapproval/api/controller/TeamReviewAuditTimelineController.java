package com.microapproval.api.controller;

import com.microapproval.api.dto.SessionAuditTimelineResponse;
import com.microapproval.api.service.TeamReviewAuditTimelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/sessions/{sessionId}/audit")
@RequiredArgsConstructor
public class TeamReviewAuditTimelineController {

    private final TeamReviewAuditTimelineService timelineService;

    @GetMapping
    public ResponseEntity<SessionAuditTimelineResponse> getTimeline(
            @PathVariable String workspaceId,
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(timelineService.getTimeline(
                workspaceId,
                sessionId,
                page,
                size,
                userDetails.getUsername()
        ));
    }
}
