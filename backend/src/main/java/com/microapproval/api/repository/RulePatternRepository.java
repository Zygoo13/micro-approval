package com.microapproval.api.repository;

import com.microapproval.api.entity.RulePattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RulePatternRepository extends JpaRepository<RulePattern, String> {
    List<RulePattern> findAllByWorkspaceIdIsNullAndIsActiveTrueOrderByPriorityAscNameAsc();
}
