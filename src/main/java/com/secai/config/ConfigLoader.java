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

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

@Configuration
@RegisterReflectionForBinding({AppConfig.class, AppConfig.OllamaConfig.class, AppConfig.OpenAIConfig.class, AppConfig.GoogleConfig.class})
public class ConfigLoader {
    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);
    private static final String DEFAULT_CONFIG_FILE = "secai.yml";

    @Bean
    public AppConfig appConfig() {
        File localConfig = Paths.get(System.getProperty("user.dir"), DEFAULT_CONFIG_FILE).toFile();
        if (localConfig.exists() && localConfig.length() > 0) {
            logger.info("Using local project configuration: {}", localConfig.getAbsolutePath());
            return loadConfig(localConfig);
        }
        
        File globalConfig = Paths.get(System.getProperty("user.home"), ".secai", DEFAULT_CONFIG_FILE).toFile();
        if (globalConfig.exists() && globalConfig.length() > 0) {
            logger.info("Using global user configuration: {}", globalConfig.getAbsolutePath());
            return loadConfig(globalConfig);
        }
        
        logger.debug("No local or global configuration found. Falling back to defaults.");
        return loadConfig(localConfig);
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
        String openaiUrl = System.getenv("OPENAI_URL");
        if ((openaiKey != null && !openaiKey.isEmpty()) || (openaiUrl != null && !openaiUrl.isEmpty())) {
            if (config.getOpenai() == null) {
                config.setOpenai(new AppConfig.OpenAIConfig());
            }
            if (openaiKey != null && !openaiKey.isEmpty()) config.getOpenai().setApiKey(openaiKey);
            if (openaiUrl != null && !openaiUrl.isEmpty()) config.getOpenai().setUrl(openaiUrl);
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
