package com.secai.ai;

import com.secai.model.Finding;
import com.secai.model.ChatMessage;
import java.util.List;

public interface AIProvider {
    /**
     * Analyzes a finding and returns an AI-generated explanation and remediation.
     * @param finding The finding to analyze.
     * @return The analyzed finding (populated with AI details).
     */
    Finding analyzeFinding(Finding finding);

    /**
     * Sends a chat prompt to the AI provider.
     * @param history The conversation history.
     * @return The AI's response.
     */
    String chat(List<ChatMessage> history);

    /**
     * Returns the name of the AI provider.
     */
    String getName();

    /**
     * Applies configuration overrides from CLI arguments.
     * @param apiKey The API key override, or null.
     * @param model The model override, or null.
     * @param url The url override, or null.
     */
    default void applyOverride(String apiKey, String model, String url) {
        // Optional operation, default is no-op
    }
}
