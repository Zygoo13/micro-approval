package com.microapproval.api.controller;

import com.microapproval.api.dto.CreateWorkspaceInvitationRequest;
import com.microapproval.api.dto.WorkspaceInvitationResponse;
import com.microapproval.api.service.WorkspaceInvitationService;
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
@RequestMapping("/api/workspaces/{workspaceId}/invitations")
@RequiredArgsConstructor
public class WorkspaceInvitationController {

    private final WorkspaceInvitationService invitationService;

    @GetMapping
    public ResponseEntity<List<WorkspaceInvitationResponse>> getWorkspaceInvitations(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(invitationService.getWorkspaceInvitations(
                workspaceId,
                userDetails.getUsername()
        ));
    }

    @PostMapping
    public ResponseEntity<WorkspaceInvitationResponse> createInvitation(
            @PathVariable String workspaceId,
            @Valid @RequestBody CreateWorkspaceInvitationRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                invitationService.createInvitation(
                        workspaceId,
                        request,
                        userDetails.getUsername()
                )
        );
    }

    @PostMapping("/{invitationId}/revoke")
    public ResponseEntity<WorkspaceInvitationResponse> revokeInvitation(
            @PathVariable String workspaceId,
            @PathVariable String invitationId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(invitationService.revokeInvitation(
                workspaceId,
                invitationId,
                userDetails.getUsername()
        ));
    }
}
