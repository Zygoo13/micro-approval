package com.microapproval.api.repository;

import com.microapproval.api.entity.MicroDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MicroDecisionRepository extends JpaRepository<MicroDecision, String> {
    // Lấy toàn bộ thẻ của một phiên, sắp xếp Rule Engine lên trước (theo display_order)
    List<MicroDecision> findAllBySessionIdOrderByDisplayOrderAsc(String sessionId);
    List<MicroDecision> findBySessionIdOrderByDisplayOrderAsc(String sessionId);
}