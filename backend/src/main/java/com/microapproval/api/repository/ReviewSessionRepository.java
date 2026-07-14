package com.microapproval.api.repository;


import com.microapproval.api.entity.ReviewSession;
import com.microapproval.api.entity.WorkspaceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewSessionRepository extends JpaRepository<ReviewSession, String> {
    // Tìm các session thuộc Personal Workspace của một user cụ thể (Bảo mật quyền riêng tư - BR-04, BR-10 trong thiet-ke-ui-va-chuan-bi-code)
    List<ReviewSession> findBySubmittedByIdAndWorkspaceTypeOrderByCreatedAtDesc(String userId, WorkspaceType workspaceType);
    Optional<ReviewSession> findByIdAndSubmittedByIdAndWorkspaceType(String id, String userId, WorkspaceType workspaceType);
    List<ReviewSession> findAllBySubmittedByIdAndWorkspaceTypeOrderByCreatedAtDesc(String userId, WorkspaceType workspaceType);

}
