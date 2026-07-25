package com.microapproval.api.repository;

import com.microapproval.api.entity.AiProviderConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AiProviderConfigurationRepository extends JpaRepository<AiProviderConfiguration, String> {
    Optional<AiProviderConfiguration> findByUserId(String userId);
}
