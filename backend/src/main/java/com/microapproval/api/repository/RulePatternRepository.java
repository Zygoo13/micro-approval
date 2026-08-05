package com.microapproval.api.repository;

import com.microapproval.api.entity.RulePattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RulePatternRepository extends JpaRepository<RulePattern, String> {
    List<RulePattern> findAllByWorkspaceIdIsNullAndIsActiveTrueOrderByPriorityAscNameAsc();

    @Query("""
            SELECT rule
            FROM RulePattern rule
            WHERE rule.isActive = true
              AND (rule.workspaceId IS NULL OR rule.workspaceId = :workspaceId)
            ORDER BY rule.priority ASC, rule.name ASC
            """)
    List<RulePattern> findActiveSystemAndWorkspaceRules(
            @Param("workspaceId") String workspaceId
    );
}
