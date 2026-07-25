package com.microapproval.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.analysis")
public class AiAnalysisProperties {
    private boolean enabled;
    private int maxCardsPerSession = 10;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getMaxCardsPerSession() { return maxCardsPerSession; }
    public void setMaxCardsPerSession(int maxCardsPerSession) { this.maxCardsPerSession = maxCardsPerSession; }
}
