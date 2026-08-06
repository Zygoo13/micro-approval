package com.microapproval.api.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "review_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewSession {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "workspace_type", nullable = false)
    private WorkspaceType workspaceType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisMode mode;

    @Lob
    @Column(name = "raw_content", nullable = false, columnDefinition = "LONGTEXT")
    private String rawContent;

    @Lob
    @Column(name = "prompt_content", columnDefinition = "TEXT")
    private String promptContent;

    @Column(name = "project_id", length = 36)
    private String projectId; // Để đơn giản hóa quan hệ trong MVP, ta lưu projectId dưới dạng String FK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by", nullable = false)
    private User submittedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    @Column(name = "is_automated")
    private Boolean isAutomated;

    @Column(name = "external_link", length = 500)
    private String externalLink;

    @Column(name = "ai_token_used")
    private Integer aiTokenUsed;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_analysis_status", nullable = false)
    private AiAnalysisStatus aiAnalysisStatus;

    @Column(name = "ai_analysis_error", length = 500)
    private String aiAnalysisError;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by_user_id")
    private User closedBy;

    @Column(name = "close_reason", length = 1000)
    private String closeReason;

    @Version
    @Column(name = "lifecycle_version", nullable = false)
    @Builder.Default
    private Long lifecycleVersion = 0L;

    // Quan hệ 1-N với MicroDecision
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<MicroDecision> decisions = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = java.util.UUID.randomUUID().toString();
        }
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = SessionStatus.PENDING;
        }
        if (this.isAutomated == null) {
            this.isAutomated = false;
        }
        if (this.aiTokenUsed == null) {
            this.aiTokenUsed = 0;
        }
        if (this.aiAnalysisStatus == null) {
            this.aiAnalysisStatus = AiAnalysisStatus.NOT_REQUESTED;
        }
    }
}
