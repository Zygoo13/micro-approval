package com.microapproval.api.dto;

import com.microapproval.api.entity.WorkspaceRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateWorkspaceInvitationRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Size(max = 255, message = "Email không được vượt quá 255 ký tự")
        String email,

        @NotNull(message = "Role không được để trống")
        WorkspaceRole role
) {
    public CreateWorkspaceInvitationRequest {
        if (email != null) {
            email = email.trim();
        }
    }
}
