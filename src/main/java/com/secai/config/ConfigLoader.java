package com.secai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class ConfigLoader {
    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);
    private static final String DEFAULT_CONFIG_FILE = "secai.yml";

    @Bean
    public AppConfig appConfig() {
        return loadConfig(Paths.get(System.getProperty("user.dir"), DEFAULT_CONFIG_FILE).toFile());
    }

    public AppConfig loadConfig(File configFile) {
        AppConfig config = new AppConfig();
        
        // 1. Load from YAML file if exists and not empty
        if (configFile.exists() && configFile.length() > 0) {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            try {
                config = mapper.readValue(configFile, AppConfig.class);
                logger.debug("Loaded configuration from {}", configFile.getAbsolutePath());
            } catch (IOException e) {
                logger.error("Failed to parse configuration file: {}", configFile.getAbsolutePath(), e);
            }
        } else {
            logger.debug("Configuration file {} not found, using defaults / environment variables", configFile.getAbsolutePath());
        }

        // 2. Override with Environment Variables
        overrideWithEnvVars(config);

        return config;
    }

    private void overrideWithEnvVars(AppConfig config) {
        String openaiKey = System.getenv("OPENAI_API_KEY");
        if (openaiKey != null && !openaiKey.isEmpty()) {
            if (config.getOpenai() == null) {
                config.setOpenai(new AppConfig.OpenAIConfig());
            }
            config.getOpenai().setApiKey(openaiKey);
        }

        String googleKey = System.getenv("GOOGLE_API_KEY");
        if (googleKey != null && !googleKey.isEmpty()) {
            if (config.getGoogle() == null) {
                config.setGoogle(new AppConfig.GoogleConfig());
            }
            config.getGoogle().setApiKey(googleKey);
        }

        String ollamaUrl = System.getenv("OLLAMA_URL");
        if (ollamaUrl != null && !ollamaUrl.isEmpty()) {
            if (config.getOllama() == null) {
                config.setOllama(new AppConfig.OllamaConfig());
            }
            config.getOllama().setUrl(ollamaUrl);
        }
    }
}
