package com.microapproval.api.dto;

import com.microapproval.api.entity.WorkspaceRole;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateWorkspaceMemberRoleRequest {

    @NotNull(message = "Role không được để trống")
    private WorkspaceRole role;
}
