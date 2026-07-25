package com.microapproval.api.service;

import com.microapproval.api.dto.*;
import com.microapproval.api.entity.*;
import com.microapproval.api.exception.InvalidOperationException;
import com.microapproval.api.exception.ResourceNotFoundException;
import com.microapproval.api.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Service @RequiredArgsConstructor
public class AiConfigurationService {
    private final AiProviderConfigurationRepository configurationRepository;
    private final UserRepository userRepository;
    private final CredentialCipher credentialCipher;
    private final AiAnalysisClient aiAnalysisClient;
    @Transactional(readOnly = true)
    public AiConfigurationResponse get(String email) { return configurationRepository.findByUserId(user(email).getId()).map(this::response).orElse(new AiConfigurationResponse(false, null, null, false, null, null)); }
    @Transactional
    public AiConfigurationResponse save(AiConfigurationRequest request, String email) {
        User user = user(email);
        AiProviderConfiguration configuration = configurationRepository.findByUserId(user.getId()).orElseGet(() -> AiProviderConfiguration.builder().userId(user.getId()).build());
        String key = request.getApiKey() == null ? "" : request.getApiKey().trim();
        if (configuration.getId() == null && key.isBlank()) throw new InvalidOperationException("Cần nhập API key khi cấu hình AI lần đầu");
        if (request.getEnabled() && configuration.getId() == null && key.isBlank()) throw new InvalidOperationException("Không thể bật AI khi chưa có API key");
        configuration.setProvider(request.getProvider()); configuration.setModel(request.getModel().trim()); configuration.setEnabled(request.getEnabled());
        if (!key.isBlank()) { configuration.setApiKeyCiphertext(credentialCipher.encrypt(key)); configuration.setApiKeySuffix(key.length() <= 4 ? "****" : key.substring(key.length() - 4)); }
        return response(configurationRepository.save(configuration));
    }
    @Transactional
    public void remove(String email) { configurationRepository.findByUserId(user(email).getId()).ifPresent(configurationRepository::delete); }
    @Transactional(readOnly = true)
    public AiConnectionTestResponse testConnection(String email) {
        AiProviderConfiguration configuration = configurationRepository.findByUserId(user(email).getId()).orElseThrow(() -> new InvalidOperationException("Bạn chưa lưu cấu hình AI"));
        aiAnalysisClient.verify(configuration);
        return new AiConnectionTestResponse(true, "Kết nối AI thành công. Bạn có thể tạo session mới để phân tích.");
    }
    @Transactional(readOnly = true)
    public Optional<AiProviderConfiguration> activeFor(User user) { return configurationRepository.findByUserId(user.getId()).filter(AiProviderConfiguration::isEnabled); }
    public String decryptKey(AiProviderConfiguration configuration) { return credentialCipher.decrypt(configuration.getApiKeyCiphertext()); }
    private User user(String email) { return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng")); }
    private AiConfigurationResponse response(AiProviderConfiguration value) { return new AiConfigurationResponse(true, value.getProvider(), value.getModel(), value.isEnabled(), value.getApiKeySuffix(), value.getUpdatedAt()); }
}
