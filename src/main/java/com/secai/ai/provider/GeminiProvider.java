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
public class GeminiProvider implements AIProvider {
    private static final Logger logger = LoggerFactory.getLogger(GeminiProvider.class);
    private String apiKey;
    private String model;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public GeminiProvider(AppConfig appConfig) {
        if (appConfig.getGoogle() != null) {
            this.apiKey = appConfig.getGoogle().getApiKey();
            this.model = appConfig.getGoogle().getModel();
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
            logger.warn("Google API key not configured. Skipping analysis for {}", finding.getId());
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
            String activeModel = model != null ? model : "gemini-1.5-pro-latest";
            
            String requestBody = """
                {
                    "systemInstruction": {
                        "parts": [ { "text": %s } ]
                    },
                    "contents": [
                        { "parts": [ { "text": %s } ] }
                    ],
                    "generationConfig": {
                        "responseMimeType": "application/json"
                    }
                }
                """.formatted(
                    mapper.writeValueAsString(systemPrompt),
                    mapper.writeValueAsString(userPrompt)
                );

            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + activeModel + ":generateContent?key=" + apiKey;
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode root = mapper.readTree(response.body());
                String aiResponseStr = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
                JsonNode aiResponse = mapper.readTree(aiResponseStr);
                
                finding.setAiExplanation(aiResponse.path("explanation").asText());
                finding.setAiRemediation(aiResponse.path("remediation").asText());
                finding.setAttackScenario(aiResponse.path("attackScenario").asText());
                finding.setSecureCodeExample(aiResponse.path("secureCodeExample").asText());
            } else {
                logger.error("Gemini API error {}: {}", response.statusCode(), response.body());
            }

        } catch (Exception e) {
            logger.error("Failed to connect to Gemini API: {}", e.getMessage());
        }

        return finding;
    }

    @Override
    public String chat(java.util.List<com.secai.model.ChatMessage> history) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "Error: Google API key not configured.";
        }
        
        try {
            String activeModel = model != null ? model : "gemini-1.5-pro-latest";
            
            StringBuilder systemParts = new StringBuilder();
            StringBuilder contentsJson = new StringBuilder("[");
            boolean firstContent = true;
            
            for (com.secai.model.ChatMessage msg : history) {
                if ("system".equalsIgnoreCase(msg.getRole())) {
                    if (systemParts.length() > 0) systemParts.append("\n");
                    systemParts.append(msg.getContent());
                } else {
                    if (!firstContent) {
                        contentsJson.append(",");
                    }
                    String geminiRole = "assistant".equalsIgnoreCase(msg.getRole()) ? "model" : "user";
                    contentsJson.append("{ \"role\": \"").append(geminiRole).append("\", \"parts\": [ { \"text\": ")
                                .append(mapper.writeValueAsString(msg.getContent())).append(" } ] }");
                    firstContent = false;
                }
            }
            contentsJson.append("]");

            StringBuilder requestBodyBuilder = new StringBuilder("{");
            if (systemParts.length() > 0) {
                requestBodyBuilder.append("\"systemInstruction\": { \"parts\": [ { \"text\": ")
                                  .append(mapper.writeValueAsString(systemParts.toString()))
                                  .append(" } ] },");
            }
            requestBodyBuilder.append("\"contents\": ").append(contentsJson.toString()).append("}");

            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + activeModel + ":generateContent?key=" + apiKey;
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyBuilder.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode root = mapper.readTree(response.body());
                return root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
            } else {
                return "Gemini API error " + response.statusCode() + ": " + response.body();
            }
        } catch (Exception e) {
            return "Failed to connect to Gemini API: " + e.getMessage();
        }
    }

    @Override
    public String getName() {
        return "gemini";
    }
}
