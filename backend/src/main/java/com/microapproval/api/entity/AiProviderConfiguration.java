package com.microapproval.api.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_provider_configurations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AiProviderConfiguration {
    @Id @Column(length = 36) private String id;
    @Column(name = "user_id", nullable = false, unique = true, length = 36) private String userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private AiProviderType provider;
    @Column(nullable = false, length = 100) private String model;
    @Column(name = "api_key_ciphertext", nullable = false, columnDefinition = "TEXT") private String apiKeyCiphertext;
    @Column(name = "api_key_suffix", nullable = false, length = 4) private String apiKeySuffix;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @PrePersist void onCreate() { if (id == null) id = java.util.UUID.randomUUID().toString(); createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}
