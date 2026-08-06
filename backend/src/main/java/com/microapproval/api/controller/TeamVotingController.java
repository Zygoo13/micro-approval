package com.microapproval.api.controller;

import com.microapproval.api.dto.SessionVotingResponse;
import com.microapproval.api.dto.UpsertTeamVoteRequest;
import com.microapproval.api.service.TeamVotingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/sessions/{sessionId}")
@RequiredArgsConstructor
public class TeamVotingController {

    private final TeamVotingService votingService;

    @GetMapping("/votes")
    public ResponseEntity<SessionVotingResponse> getSessionVotes(
            @PathVariable String workspaceId,
            @PathVariable String sessionId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(votingService.getSessionVotes(
                workspaceId,
                sessionId,
                userDetails.getUsername()
        ));
    }

    @PutMapping("/cards/{cardId}/vote")
    public ResponseEntity<SessionVotingResponse> upsertOwnVote(
            @PathVariable String workspaceId,
            @PathVariable String sessionId,
            @PathVariable String cardId,
            @Valid @RequestBody UpsertTeamVoteRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(votingService.upsertOwnVote(
                workspaceId,
                sessionId,
                cardId,
                request,
                userDetails.getUsername()
        ));
    }
}
