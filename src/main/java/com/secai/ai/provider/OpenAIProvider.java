package com.secai.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secai.ai.AIProvider;
import com.secai.config.AppConfig;
import com.secai.model.Finding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class OpenAIProvider implements AIProvider {
    private static final Logger logger = LoggerFactory.getLogger(OpenAIProvider.class);
    private String apiKey;
    private String model;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public OpenAIProvider(AppConfig appConfig) {
        if (appConfig.getOpenai() != null) {
            this.apiKey = appConfig.getOpenai().getApiKey();
            this.model = appConfig.getOpenai().getModel();
        }
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.mapper = new ObjectMapper();
    }

    @Override
    public void applyOverride(String apiKey, String model, String url) {
        if (apiKey != null && !apiKey.isEmpty()) {
            this.apiKey = apiKey;
        }
        if (model != null && !model.isEmpty()) {
            this.model = model;
        }
    }

    @Override
    public Finding analyzeFinding(Finding finding) {
        if (apiKey == null || apiKey.isEmpty()) {
            logger.warn("OpenAI API key not configured. Skipping analysis for {}", finding.getId());
            return finding;
        }

        String systemPrompt = "You are an expert Application Security Engineer. Analyze the following security finding. " +
                "Respond in strict JSON format with exactly four keys: 'explanation' (brief explanation of the vulnerability), " +
                "'remediation' (how to fix it securely), 'attackScenario' (a hypothetical attack exploiting this), " +
                "and 'secureCodeExample' (a short code snippet showing the fixed code).";
                
        String userPrompt = "Finding from " + finding.getScannerName() + ":\n" +
                "Title: " + finding.getTitle() + "\n" +
                "Description: " + finding.getDescription() + "\n" +
                "File: " + finding.getFile() + "\n" +
                "Severity: " + finding.getSeverity();

        try {
            String activeModel = model != null ? model : "gpt-4-turbo-preview";
            String requestBody = """
                {
                    "model": "%s",
                    "response_format": { "type": "json_object" },
                    "messages": [
                        { "role": "system", "content": %s },
                        { "role": "user", "content": %s }
                    ]
                }
                """.formatted(
                    activeModel,
                    mapper.writeValueAsString(systemPrompt),
                    mapper.writeValueAsString(userPrompt)
                );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode root = mapper.readTree(response.body());
                String aiResponseStr = root.path("choices").get(0).path("message").path("content").asText();
                JsonNode aiResponse = mapper.readTree(aiResponseStr);
                
                finding.setAiExplanation(aiResponse.path("explanation").asText());
                finding.setAiRemediation(aiResponse.path("remediation").asText());
                finding.setAttackScenario(aiResponse.path("attackScenario").asText());
                finding.setSecureCodeExample(aiResponse.path("secureCodeExample").asText());
            } else {
                logger.error("OpenAI API error {}: {}", response.statusCode(), response.body());
            }

        } catch (Exception e) {
            logger.error("Failed to connect to OpenAI API: {}", e.getMessage());
        }

        return finding;
    }

    @Override
    public String chat(java.util.List<com.secai.model.ChatMessage> history) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "Error: OpenAI API key not configured.";
        }
        
        try {
            StringBuilder messagesJson = new StringBuilder("[");
            for (int i = 0; i < history.size(); i++) {
                com.secai.model.ChatMessage msg = history.get(i);
                messagesJson.append("{ \"role\": \"").append(msg.getRole()).append("\", \"content\": ")
                            .append(mapper.writeValueAsString(msg.getContent())).append(" }");
                if (i < history.size() - 1) {
                    messagesJson.append(",");
                }
            }
            messagesJson.append("]");

            String activeModel = model != null ? model : "gpt-4-turbo-preview";
            String requestBody = """
                {
                    "model": "%s",
                    "messages": %s
                }
                """.formatted(
                    activeModel,
                    messagesJson.toString()
                );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = mapper.readTree(response.body());
                return root.path("choices").get(0).path("message").path("content").asText();
            } else {
                return "OpenAI API error " + response.statusCode() + ": " + response.body();
            }
        } catch (Exception e) {
            return "Failed to connect to OpenAI API: " + e.getMessage();
        }
    }

    @Override
    public String getName() {
        return "openai";
    }
}
