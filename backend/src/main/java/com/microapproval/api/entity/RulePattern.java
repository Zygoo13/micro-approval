package com.microapproval.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "rule_patterns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RulePattern {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "workspace_id", length = 36)
    private String workspaceId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Integer priority;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String pattern;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_category", nullable = false)
    private RiskCategory riskCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false)
    private RiskLevel riskLevel;

    @Column(name = "question_template", nullable = false, columnDefinition = "TEXT")
    private String questionTemplate;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (priority == null) {
            priority = 100;
        }
        if (isActive == null) {
            isActive = true;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
