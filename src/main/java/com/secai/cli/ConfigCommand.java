package com.secai.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.secai.config.AppConfig;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Option(names = {"--local"}, description = "Save to local project directory instead of global user directory")
    private boolean local;

    @Autowired
    private org.springframework.beans.factory.ObjectProvider<SecAiCommand> secAiCommandProvider;

    @Override
    public Integer call() {
        File configFile;
        if (local) {
            configFile = Paths.get(System.getProperty("user.dir"), "secai.yml").toFile();
        } else {
            File globalDir = Paths.get(System.getProperty("user.home"), ".secai").toFile();
            if (!globalDir.exists()) {
                globalDir.mkdirs();
            }
            configFile = new File(globalDir, "secai.yml");
        }
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        AppConfig config = new AppConfig();

        if (configFile.exists()) {
            try {
                config = mapper.readValue(configFile, AppConfig.class);
            } catch (IOException e) {
                System.out.println("Error reading existing secai.yml: " + e.getMessage());
            }
        }

        SecAiCommand secAiCommand = secAiCommandProvider.getIfAvailable();
        
        String actualProvider = provider != null ? provider : (secAiCommand != null ? secAiCommand.getAiProvider() : null);
        String actualApiKey = apiKey != null ? apiKey : (secAiCommand != null ? secAiCommand.getAiApiKey() : null);
        String actualModel = model != null ? model : (secAiCommand != null ? secAiCommand.getAiModel() : null);
        String actualUrl = url != null ? url : (secAiCommand != null ? secAiCommand.getAiUrl() : null);

        if (actualProvider != null) {
            config.setProvider(actualProvider);
            System.out.println("Set provider to: " + actualProvider);
        }

        String targetProvider = actualProvider != null ? actualProvider : (config.getProvider() != null ? config.getProvider() : "");

        if (targetProvider.equalsIgnoreCase("openai")) {
            if (config.getOpenai() == null) config.setOpenai(new AppConfig.OpenAIConfig());
            if (actualApiKey != null) { config.getOpenai().setApiKey(actualApiKey); System.out.println("Set OpenAI API Key."); }
            if (actualModel != null) { config.getOpenai().setModel(actualModel); System.out.println("Set OpenAI Model: " + actualModel); }
            if (actualUrl != null) { config.getOpenai().setUrl(actualUrl); System.out.println("Set OpenAI URL: " + actualUrl); }
        } else if (targetProvider.equalsIgnoreCase("gemini")) {
            if (config.getGoogle() == null) config.setGoogle(new AppConfig.GoogleConfig());
            if (actualApiKey != null) { config.getGoogle().setApiKey(actualApiKey); System.out.println("Set Gemini API Key."); }
            if (actualModel != null) { config.getGoogle().setModel(actualModel); System.out.println("Set Gemini Model: " + actualModel); }
        } else if (targetProvider.equalsIgnoreCase("ollama")) {
            if (config.getOllama() == null) config.setOllama(new AppConfig.OllamaConfig());
            if (actualUrl != null) { config.getOllama().setUrl(actualUrl); System.out.println("Set Ollama URL: " + actualUrl); }
            if (actualModel != null) { config.getOllama().setModel(actualModel); System.out.println("Set Ollama Model: " + actualModel); }
        } else if (actualProvider == null && (actualApiKey != null || actualModel != null || actualUrl != null)) {
            System.out.println("Please specify a --provider when setting keys or models.");
            return 1;
        }

        try {
            mapper.writeValue(configFile, config);
            System.out.println("Configuration saved to " + configFile.getAbsolutePath());
            
            if (!local) {
                File localConfig = Paths.get(System.getProperty("user.dir"), "secai.yml").toFile();
                if (localConfig.exists() && localConfig.length() > 0) {
                    System.out.println("\n\033[33m[WARNING] A local secai.yml exists in the current directory:");
                    System.out.println(localConfig.getAbsolutePath());
                    System.out.println("This local file will override the global configuration you just saved!\033[0m");
                }
            }
        } catch (IOException e) {
            System.out.println("Error writing config file: " + e.getMessage());
            return 1;
        }

        return 0;
    }
}
