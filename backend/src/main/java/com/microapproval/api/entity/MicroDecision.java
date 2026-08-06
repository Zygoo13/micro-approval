package com.microapproval.api.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "micro_decisions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MicroDecision {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ReviewSession session;

    @Enumerated(EnumType.STRING)
    @Column(name = "engine_type", nullable = false)
    private EngineType engineType;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_category", nullable = false)
    private RiskCategory riskCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false)
    private RiskLevel riskLevel;

    @Lob
    @Column(name = "code_snippet", columnDefinition = "TEXT")
    private String codeSnippet;

    @Lob
    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "is_ai_bypassed")
    private Boolean isAiBypassed;

    @Enumerated(EnumType.STRING)
    @Column(name = "human_decision", nullable = false)
    private DecisionStatus humanDecision;

    @Enumerated(EnumType.STRING)
    @Column(name = "team_decision")
    private TeamDecisionStatus teamDecision;

    @Lob
    @Column(name = "reviewer_note", columnDefinition = "TEXT")
    private String reviewerNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = java.util.UUID.randomUUID().toString();
        }
        if (this.humanDecision == null) {
            this.humanDecision = DecisionStatus.PENDING;
        }
        if (this.teamDecision == null
                && this.session != null
                && this.session.getWorkspaceType() == WorkspaceType.SHARED) {
            this.teamDecision = TeamDecisionStatus.PENDING;
        }
        if (this.isAiBypassed == null) {
            this.isAiBypassed = false;
        }
    }
}
