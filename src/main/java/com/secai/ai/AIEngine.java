package com.secai.ai;

import com.secai.model.Finding;
import com.secai.model.ChatMessage;
import com.secai.cli.SecAiCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.secai.config.AppConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AIEngine {
    private static final Logger logger = LoggerFactory.getLogger(AIEngine.class);
    private final List<AIProvider> providers;
    private final AppConfig appConfig;
    private final ObjectProvider<SecAiCommand> secAiCommandProvider;

    @Autowired
    public AIEngine(AppConfig appConfig, List<AIProvider> providers, ObjectProvider<SecAiCommand> secAiCommandProvider) {
        this.appConfig = appConfig;
        this.providers = providers;
        this.secAiCommandProvider = secAiCommandProvider;
    }
    
    private AIProvider getActiveProvider() {
        SecAiCommand secAiCommand = secAiCommandProvider.getIfAvailable();
        String configuredProvider = (secAiCommand != null && secAiCommand.getAiProvider() != null) 
                                        ? secAiCommand.getAiProvider() 
                                        : appConfig.getProvider();
        AIProvider activeProvider = null;
        
        if (configuredProvider != null) {
            for (AIProvider provider : providers) {
                if (provider.getName().equalsIgnoreCase(configuredProvider)) {
                    activeProvider = provider;
                    break;
                }
            }
        }
        
        if (activeProvider == null && !providers.isEmpty()) {
            activeProvider = providers.get(0); // fallback
            if (configuredProvider != null) {
                logger.warn("Configured AI provider '{}' not found. Falling back to {}.", configuredProvider, activeProvider.getName());
            } else {
                logger.debug("No AI provider configured. Defaulting to {}.", activeProvider.getName());
            }
        }
        
        // Pass CLI overrides to the provider if it supports it
        if (activeProvider != null && secAiCommand != null) {
            if (secAiCommand.getAiApiKey() != null || secAiCommand.getAiModel() != null || secAiCommand.getAiUrl() != null) {
                activeProvider.applyOverride(secAiCommand.getAiApiKey(), secAiCommand.getAiModel(), secAiCommand.getAiUrl());
            }
        }
        
        return activeProvider;
    }

    public void analyzeFindings(List<Finding> findings) {
        AIProvider activeProvider = getActiveProvider();
        if (activeProvider == null) {
            logger.warn("No AI provider configured. Skipping AI analysis.");
            return;
        }

        logger.info("Starting AI analysis using provider: {}", activeProvider.getName());
        for (Finding finding : findings) {
            try {
                activeProvider.analyzeFinding(finding);
                logger.debug("Successfully analyzed finding: {}", finding.getTitle());
            } catch (Exception e) {
                logger.error("Failed to analyze finding {}: {}", finding.getTitle(), e.getMessage());
            }
        }
    }

    public String chat(List<ChatMessage> history) {
        AIProvider activeProvider = getActiveProvider();
        if (activeProvider == null) {
            return "Error: No AI provider configured.";
        }
        return activeProvider.chat(history);
    }
}
