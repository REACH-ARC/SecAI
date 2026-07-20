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
                sanitizeFinding(finding);
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
        return sanitizeOutput(activeProvider.chat(history));
    }

    private void sanitizeFinding(Finding finding) {
        if (finding.getAiExplanation() != null) finding.setAiExplanation(sanitizeOutput(finding.getAiExplanation()));
        if (finding.getAiRemediation() != null) finding.setAiRemediation(sanitizeOutput(finding.getAiRemediation()));
        if (finding.getAttackScenario() != null) finding.setAttackScenario(sanitizeOutput(finding.getAttackScenario()));
    }

    private String sanitizeOutput(String input) {
        if (input == null) return null;
        boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
        if (!isWindows) return input;

        // 1. Replace known punctuation and math symbols with ASCII equivalents
        String sanitized = input.replace("\u2011", "-") // non-breaking hyphen
                    .replace("\u2012", "-") // figure dash
                    .replace("\u2013", "-") // en dash
                    .replace("\u2014", "-") // em dash
                    .replace("\u2018", "'") // left single quote
                    .replace("\u2019", "'") // right single quote
                    .replace("\u201C", "\"") // left double quote
                    .replace("\u201D", "\"") // right double quote
                    .replace("\u2026", "...") // ellipsis
                    .replace("\u202F", " ") // narrow no-break space
                    .replace("\u00A0", " ") // no-break space
                    .replace("\u2248", "~=") // almost equal to
                    .replace("\u2265", ">=") // greater than or equal to
                    .replace("\u2264", "<=") // less than or equal to
                    .replace("\u2705", "[OK]") // check mark
                    .replace("\u2713", "[OK]") // light check mark
                    .replace("\u2714", "[OK]") // heavy check mark
                    .replace("\u274C", "[X]") // cross mark
                    .replace("\u2717", "[X]") // light cross mark
                    .replace("\u2718", "[X]"); // heavy cross mark

        // 2. Strip all remaining non-ASCII characters (this elegantly removes Emojis, 
        // leaving behind their base numbers like '1' from '1️⃣')
        return sanitized.replaceAll("[^\\x00-\\x7F]", "");
    }
}
