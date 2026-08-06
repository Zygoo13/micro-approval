package com.microapproval.api.repository;

import com.microapproval.api.entity.MicroDecision;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MicroDecisionRepository extends JpaRepository<MicroDecision, String> {
    // Lấy toàn bộ thẻ của một phiên, sắp xếp Rule Engine lên trước (theo display_order)
    List<MicroDecision> findAllBySessionIdOrderByDisplayOrderAsc(String sessionId);
    List<MicroDecision> findBySessionIdOrderByDisplayOrderAsc(String sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT card
            FROM MicroDecision card
            WHERE card.id = :cardId
              AND card.session.id = :sessionId
            """)
    Optional<MicroDecision> findByIdAndSessionIdForUpdate(
            @Param("cardId") String cardId,
            @Param("sessionId") String sessionId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT card
            FROM MicroDecision card
            WHERE card.session.id = :sessionId
            ORDER BY card.displayOrder ASC, card.id ASC
            """)
    List<MicroDecision> findAllBySessionIdForUpdate(@Param("sessionId") String sessionId);
}
