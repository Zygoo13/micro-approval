package com.microapproval.api.controller;

import com.microapproval.api.dto.CreateWorkspaceRequest;
import com.microapproval.api.dto.WorkspaceResponse;
import com.microapproval.api.dto.WorkspaceSummaryResponse;
import com.microapproval.api.service.WorkspaceService;
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
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @PostMapping
    public ResponseEntity<WorkspaceResponse> createWorkspace(
            @Valid @RequestBody CreateWorkspaceRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        WorkspaceResponse response =
                workspaceService.createWorkspace(request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<WorkspaceSummaryResponse>> getMyWorkspaces(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(workspaceService.getMyWorkspaces(userDetails.getUsername()));
    }

    @GetMapping("/{workspaceId}")
    public ResponseEntity<WorkspaceResponse> getWorkspace(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(
                workspaceService.getWorkspace(workspaceId, userDetails.getUsername())
        );
    }
}
