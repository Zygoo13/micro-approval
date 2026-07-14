package com.microapproval.api.controller;

import com.microapproval.api.dto.CreatePersonalSessionRequest;
import com.microapproval.api.dto.DecisionVoteRequest;
import com.microapproval.api.entity.MicroDecision;
import com.microapproval.api.entity.ReviewSession;
import com.microapproval.api.service.PersonalSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/personal/sessions")
@RequiredArgsConstructor
public class PersonalSessionController {

    private final PersonalSessionService sessionService;

    // API: POST http://localhost:8080/api/v1/personal/sessions
    @PostMapping
    public ResponseEntity<ReviewSession> createPersonalSession(
            @RequestBody CreatePersonalSessionRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        // Trích xuất email thông qua userDetails.getUsername() [1]
        return ResponseEntity.ok(sessionService.createSession(request, userDetails.getUsername()));
    }

    // API: GET http://localhost:8080/api/v1/personal/sessions
    @GetMapping
    public ResponseEntity<List<ReviewSession>> getMyPersonalSessions(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(sessionService.getMyPersonalSessions(userDetails.getUsername()));
    }

    // API: PATCH http://localhost:8080/api/v1/personal/sessions/decisions/{decisionId}
    @PatchMapping("/decisions/{decisionId}")
    public ResponseEntity<MicroDecision> voteDecision(
            @PathVariable String decisionId,
            @RequestBody DecisionVoteRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(sessionService.voteDecision(decisionId, request, userDetails.getUsername()));
    }

}