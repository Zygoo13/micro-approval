package com.microapproval.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microapproval.api.entity.AiProviderConfiguration;
import com.microapproval.api.entity.AiProviderType;
import com.microapproval.api.exception.AiProviderException;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import java.util.*;

@Service
public class ProviderAiAnalysisClient implements AiAnalysisClient {
    private static final String SYSTEM_PROMPT = """
            You are a code-review assistant. Analyze only supplied code not already matched by deterministic rules.
            Return JSON only: {\"decisions\":[{\"riskCategory\":\"SECURITY|DATABASE|DEPENDENCY|BUSINESS_LOGIC|INTENT_GAP\",\"riskLevel\":\"LOW|MEDIUM|HIGH\",\"codeSnippet\":\"short exact excerpt\",\"questionText\":\"a concise Vietnamese review question\"}]}.
            Never approve or reject; only ask review questions. Return an empty decisions array when no meaningful risk exists.
            """;
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CredentialCipher credentialCipher;
    public ProviderAiAnalysisClient(CredentialCipher credentialCipher) { this.credentialCipher = credentialCipher; }
    @Override public AiAnalysisResult analyze(AiProviderConfiguration configuration, String remainingContent) {
        String apiKey = credentialCipher.decrypt(configuration.getApiKeyCiphertext());
        try { return configuration.getProvider() == AiProviderType.OPENAI ? openAi(configuration, apiKey, remainingContent) : gemini(configuration, apiKey, remainingContent); }
        catch (RestClientResponseException exception) { throw providerError(exception); }
        catch (AiProviderException exception) { throw exception; }
        catch (Exception exception) { throw new AiProviderException("Không thể kết nối AI provider. Hãy thử lại sau.", exception); }
    }
    @Override public void verify(AiProviderConfiguration configuration) {
        String key = credentialCipher.decrypt(configuration.getApiKeyCiphertext());
        try {
            if (configuration.getProvider() == AiProviderType.OPENAI) restClient.post().uri("https://api.openai.com/v1/chat/completions").header(HttpHeaders.AUTHORIZATION, "Bearer " + key).contentType(MediaType.APPLICATION_JSON).body(Map.of("model", configuration.getModel(), "max_tokens", 1, "messages", List.of(Map.of("role", "user", "content", "Reply OK")))).retrieve().toBodilessEntity();
            else restClient.post().uri("https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent", configuration.getModel()).header("x-goog-api-key", key).contentType(MediaType.APPLICATION_JSON).body(Map.of("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", "Reply OK")))), "generationConfig", Map.of("maxOutputTokens", 1))).retrieve().toBodilessEntity();
        } catch (RestClientResponseException exception) { throw providerError(exception); }
        catch (AiProviderException exception) { throw exception; }
        catch (Exception exception) { throw new AiProviderException("Không thể kết nối AI provider. Hãy thử lại sau.", exception); }
    }
    private AiAnalysisResult openAi(AiProviderConfiguration c, String key, String content) throws Exception {
        Map<String, Object> body = Map.of("model", c.getModel(), "response_format", Map.of("type", "json_object"), "messages", List.of(Map.of("role", "system", "content", SYSTEM_PROMPT), Map.of("role", "user", "content", content)));
        JsonNode response = objectMapper.readTree(restClient.post().uri("https://api.openai.com/v1/chat/completions").header(HttpHeaders.AUTHORIZATION, "Bearer " + key).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(String.class));
        return payload(response.path("choices").path(0).path("message").path("content").asText(), response.path("usage").path("total_tokens").asInt(0));
    }
    private AiAnalysisResult gemini(AiProviderConfiguration c, String key, String content) throws Exception {
        Map<String, Object> body = Map.of("system_instruction", Map.of("parts", List.of(Map.of("text", SYSTEM_PROMPT))), "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", content)))), "generationConfig", Map.of("responseMimeType", "application/json"));
        JsonNode response = objectMapper.readTree(restClient.post().uri("https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent", c.getModel()).header("x-goog-api-key", key).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(String.class));
        return payload(response.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText(), response.path("usageMetadata").path("totalTokenCount").asInt(0));
    }
    private AiAnalysisResult payload(String json, int tokens) throws Exception {
        String normalized = json == null ? "" : json.trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        JsonNode decisions = objectMapper.readTree(normalized).path("decisions");
        List<AiDecisionCandidate> result = new ArrayList<>();
        if (decisions.isArray()) for (JsonNode item : decisions) result.add(objectMapper.treeToValue(item, AiDecisionCandidate.class));
        return new AiAnalysisResult(result, tokens);
    }
    private AiProviderException providerError(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == 401 || status == 403) return new AiProviderException("AI provider từ chối API key. Hãy kiểm tra key và quyền truy cập model.");
        if (status == 404) return new AiProviderException("Model không tồn tại hoặc provider không hỗ trợ model này.");
        if (status == 429) return new AiProviderException("AI provider đang giới hạn quota/rate limit. Hãy kiểm tra billing hoặc thử lại sau.");
        return new AiProviderException("AI provider trả về lỗi " + status + ". Hãy thử lại sau.");
    }
}
