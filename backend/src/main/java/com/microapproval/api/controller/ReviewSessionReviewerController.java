package com.microapproval.api.controller;

import com.microapproval.api.dto.AssignSessionReviewerRequest;
import com.microapproval.api.dto.RemoveSessionReviewerRequest;
import com.microapproval.api.dto.SessionReviewerResponse;
import com.microapproval.api.service.ReviewSessionReviewerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/workspaces/{workspaceId}/sessions/{sessionId}/reviewers")
@RequiredArgsConstructor
public class ReviewSessionReviewerController {

    private final ReviewSessionReviewerService reviewerService;

    @GetMapping
    public ResponseEntity<List<SessionReviewerResponse>> getReviewers(
            @PathVariable String workspaceId,
            @PathVariable String sessionId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(reviewerService.getReviewers(
                workspaceId,
                sessionId,
                userDetails.getUsername()
        ));
    }

    @PostMapping
    public ResponseEntity<SessionReviewerResponse> assignReviewer(
            @PathVariable String workspaceId,
            @PathVariable String sessionId,
            @Valid @RequestBody AssignSessionReviewerRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(
                reviewerService.assignReviewer(
                        workspaceId,
                        sessionId,
                        request,
                        userDetails.getUsername()
                )
        );
    }

    @PostMapping("/{reviewerAssignmentId}/remove")
    public ResponseEntity<SessionReviewerResponse> removeReviewer(
            @PathVariable String workspaceId,
            @PathVariable String sessionId,
            @PathVariable String reviewerAssignmentId,
            @Valid @RequestBody RemoveSessionReviewerRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(reviewerService.removeReviewer(
                workspaceId,
                sessionId,
                reviewerAssignmentId,
                request,
                userDetails.getUsername()
        ));
    }
}
