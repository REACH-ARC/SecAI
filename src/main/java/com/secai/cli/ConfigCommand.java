package com.secai.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.secai.config.AppConfig;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

@Component
@Command(name = "config", description = "Manage configuration settings in secai.yml.")
public class ConfigCommand implements Callable<Integer> {

    @Option(names = {"--provider"}, description = "Set the active AI provider (openai, gemini, ollama)")
    private String provider;

    @Option(names = {"--api-key"}, description = "Set the API key for the selected provider")
    private String apiKey;

    @Option(names = {"--model"}, description = "Set the AI model to use")
    private String model;

    @Option(names = {"--url"}, description = "Set the URL (mainly for ollama)")
    private String url;

    @Override
    public Integer call() {
        File configFile = Paths.get(System.getProperty("user.dir"), "secai.yml").toFile();
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        AppConfig config = new AppConfig();

        if (configFile.exists()) {
            try {
                config = mapper.readValue(configFile, AppConfig.class);
            } catch (IOException e) {
                System.out.println("Error reading existing secai.yml: " + e.getMessage());
            }
        }

        if (provider != null) {
            config.setProvider(provider);
            System.out.println("Set provider to: " + provider);
        }

        String targetProvider = provider != null ? provider : (config.getProvider() != null ? config.getProvider() : "");

        if (targetProvider.equalsIgnoreCase("openai")) {
            if (config.getOpenai() == null) config.setOpenai(new AppConfig.OpenAIConfig());
            if (apiKey != null) { config.getOpenai().setApiKey(apiKey); System.out.println("Set OpenAI API Key."); }
            if (model != null) { config.getOpenai().setModel(model); System.out.println("Set OpenAI Model: " + model); }
        } else if (targetProvider.equalsIgnoreCase("gemini")) {
            if (config.getGoogle() == null) config.setGoogle(new AppConfig.GoogleConfig());
            if (apiKey != null) { config.getGoogle().setApiKey(apiKey); System.out.println("Set Gemini API Key."); }
            if (model != null) { config.getGoogle().setModel(model); System.out.println("Set Gemini Model: " + model); }
        } else if (targetProvider.equalsIgnoreCase("ollama")) {
            if (config.getOllama() == null) config.setOllama(new AppConfig.OllamaConfig());
            if (url != null) { config.getOllama().setUrl(url); System.out.println("Set Ollama URL: " + url); }
            if (model != null) { config.getOllama().setModel(model); System.out.println("Set Ollama Model: " + model); }
        } else if (provider == null && (apiKey != null || model != null || url != null)) {
            System.out.println("Please specify a --provider when setting keys or models.");
            return 1;
        }

        try {
            mapper.writeValue(configFile, config);
            System.out.println("Configuration saved to " + configFile.getAbsolutePath());
        } catch (IOException e) {
            System.out.println("Error writing config file: " + e.getMessage());
            return 1;
        }

        return 0;
    }
}
