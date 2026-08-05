package com.microapproval.api.controller;

import com.microapproval.api.dto.AddWorkspaceMemberRequest;
import com.microapproval.api.dto.UpdateWorkspaceMemberRoleRequest;
import com.microapproval.api.dto.WorkspaceMemberResponse;
import com.microapproval.api.service.WorkspaceMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/members")
@RequiredArgsConstructor
public class WorkspaceMemberController {

    private final WorkspaceMemberService workspaceMemberService;

    @GetMapping
    public ResponseEntity<List<WorkspaceMemberResponse>> getMembers(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(
                workspaceMemberService.getMembers(workspaceId, userDetails.getUsername())
        );
    }

    @PostMapping
    public ResponseEntity<WorkspaceMemberResponse> addMember(
            @PathVariable String workspaceId,
            @Valid @RequestBody AddWorkspaceMemberRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(
                workspaceMemberService.addMember(
                        workspaceId,
                        request,
                        userDetails.getUsername()
                )
        );
    }

    @PatchMapping("/{memberId}/role")
    public ResponseEntity<WorkspaceMemberResponse> changeMemberRole(
            @PathVariable String workspaceId,
            @PathVariable String memberId,
            @Valid @RequestBody UpdateWorkspaceMemberRoleRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(
                workspaceMemberService.changeMemberRole(
                        workspaceId,
                        memberId,
                        request,
                        userDetails.getUsername()
                )
        );
    }

    @DeleteMapping("/{memberId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable String workspaceId,
            @PathVariable String memberId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        workspaceMemberService.removeMember(workspaceId, memberId, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
