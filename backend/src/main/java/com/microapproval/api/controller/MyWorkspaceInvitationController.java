package com.microapproval.api.controller;

import com.microapproval.api.dto.MyWorkspaceInvitationResponse;
import com.microapproval.api.dto.WorkspaceInvitationResponse;
import com.microapproval.api.service.WorkspaceInvitationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/workspace-invitations")
@RequiredArgsConstructor
public class MyWorkspaceInvitationController {

    private final WorkspaceInvitationService invitationService;

    @GetMapping("/mine")
    public ResponseEntity<List<MyWorkspaceInvitationResponse>> getMyInvitations(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(
                invitationService.getMyInvitations(userDetails.getUsername())
        );
    }

    @PostMapping("/{invitationId}/accept")
    public ResponseEntity<WorkspaceInvitationResponse> acceptInvitation(
            @PathVariable String invitationId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(invitationService.acceptInvitation(
                invitationId,
                userDetails.getUsername()
        ));
    }

    @PostMapping("/{invitationId}/reject")
    public ResponseEntity<WorkspaceInvitationResponse> rejectInvitation(
            @PathVariable String invitationId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(invitationService.rejectInvitation(
                invitationId,
                userDetails.getUsername()
        ));
    }
}
