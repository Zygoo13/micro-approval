package com.microapproval.api.controller;

import com.microapproval.api.dto.CreateSharedReviewSessionRequest;
import com.microapproval.api.dto.CloseSharedReviewSessionRequest;
import com.microapproval.api.dto.SharedReviewSessionDetailResponse;
import com.microapproval.api.dto.SharedReviewSessionLifecycleResponse;
import com.microapproval.api.dto.SharedReviewSessionSummaryResponse;
import com.microapproval.api.service.SharedReviewSessionService;
import com.microapproval.api.service.SharedReviewSessionLifecycleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/sessions")
@RequiredArgsConstructor
public class SharedReviewSessionController {

    private final SharedReviewSessionService sessionService;
    private final SharedReviewSessionLifecycleService lifecycleService;

    @PostMapping
    public ResponseEntity<SharedReviewSessionDetailResponse> createSession(
            @PathVariable String workspaceId,
            @Valid @RequestBody CreateSharedReviewSessionRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                sessionService.createSession(workspaceId, request, userDetails.getUsername())
        );
    }

    @GetMapping
    public ResponseEntity<List<SharedReviewSessionSummaryResponse>> getSessions(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(
                sessionService.getSessions(workspaceId, userDetails.getUsername())
        );
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<SharedReviewSessionDetailResponse> getSessionDetail(
            @PathVariable String workspaceId,
            @PathVariable String sessionId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(sessionService.getSessionDetail(
                workspaceId,
                sessionId,
                userDetails.getUsername()
        ));
    }

    @PostMapping("/{sessionId}/close")
    public ResponseEntity<SharedReviewSessionLifecycleResponse> closeSession(
            @PathVariable String workspaceId,
            @PathVariable String sessionId,
            @Valid @RequestBody(required = false) CloseSharedReviewSessionRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(lifecycleService.closeSession(
                workspaceId,
                sessionId,
                request,
                userDetails.getUsername()
        ));
    }

    @PostMapping("/{sessionId}/reopen")
    public ResponseEntity<SharedReviewSessionLifecycleResponse> reopenSession(
            @PathVariable String workspaceId,
            @PathVariable String sessionId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(lifecycleService.reopenSession(
                workspaceId,
                sessionId,
                userDetails.getUsername()
        ));
    }
}
