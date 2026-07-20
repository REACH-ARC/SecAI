package com.secai.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;
import org.springframework.beans.factory.annotation.Autowired;
import com.secai.scanner.ScannerEngine;
import com.secai.scanner.ScannerProvider;
import com.secai.config.AppConfig;
import com.secai.util.CommandParserUtils;

@Component
@Command(
    name = "secai",
    mixinStandardHelpOptions = true,
    version = "secai 1.0",
    description = "AI-Powered Security Analysis CLI.",
    subcommands = {
        ScanCommand.class,
        ExplainCommand.class,
        FixCommand.class,
        ReportCommand.class,
        ConfigCommand.class,
        DoctorCommand.class,
        UpdateCommand.class,
        ChatCommand.class,
        ListCommand.class
    }
)
public class SecAiCommand implements Callable<Integer> {

    @Autowired
    private ScannerEngine scannerEngine;

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private CommandLine.IFactory factory;

    private boolean inRepl = false;

    @Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
    public boolean verbose;

    @Option(names = {"--ai-provider"}, description = "AI provider to use (gemini, openai, ollama)", scope = CommandLine.ScopeType.INHERIT)
    public String aiProvider;

    @Option(names = {"--ai-api-key"}, description = "API key for the chosen AI provider", scope = CommandLine.ScopeType.INHERIT)
    public String aiApiKey;

    @Option(names = {"--ai-model"}, description = "Model to use for the AI provider", scope = CommandLine.ScopeType.INHERIT)
    public String aiModel;

    @Option(names = {"--ai-url"}, description = "URL for the AI provider (e.g., for Ollama)", scope = CommandLine.ScopeType.INHERIT)
    public String aiUrl;

    public String getAiProvider() {
        return aiProvider;
    }

    public String getAiApiKey() {
        return aiApiKey;
    }

    public String getAiModel() {
        return aiModel;
    }

    public String getAiUrl() {
        return aiUrl;
    }

    @Override
    public Integer call() {
        if (inRepl) {
            System.out.println("Already in interactive mode.");
            return 0;
        }
        
        inRepl = true;

        System.out.println("""
                 _____           _    ___ 
                /  ___|         / \\  |_ _|
                \\ `--.  ___  ___\\  \\  | | 
                 `--. \\/ _ \\/ __|  _ \\ | | 
                /\\__/ /  __/ (__| | \\ \\| | 
                \\____/ \\___|\\___\\_|  \\_\\___|
                """);
        System.out.println("AI-Powered Security Analysis CLI (v1.0)\n");

        System.out.println("--- Scanner Status ---");
        boolean anyMissing = false;
        if (scannerEngine != null && scannerEngine.getProviders() != null) {
            for (ScannerProvider provider : scannerEngine.getProviders()) {
                if (provider.isAvailable()) {
                    System.out.println("[OK] " + provider.getName() + " is installed and ready.");
                } else {
                    System.out.println("[X] " + provider.getName() + " is MISSING.");
                    anyMissing = true;
                }
            }
        }

        if (anyMissing) {
            String os = System.getProperty("os.name").toLowerCase();
            System.out.println("\n--- Installation Suggestions ---");
            if (os.contains("win")) {
                System.out.println("Windows: Run 'pip install semgrep' and 'winget install Aquasecurity.Trivy'");
            } else if (os.contains("mac")) {
                System.out.println("macOS: Run 'brew install semgrep trivy'");
            } else {
                System.out.println("Linux: Run 'pip install semgrep' and 'sudo apt-get install trivy'");
            }
        }

        System.out.println("\n--- AI Configuration Status ---");
        if (appConfig != null && appConfig.getProvider() != null && !appConfig.getProvider().isEmpty()) {
            System.out.println("[OK] AI Provider: " + appConfig.getProvider() + " is configured.");
            if ("ollama".equalsIgnoreCase(appConfig.getProvider()) && appConfig.getOllama() != null) {
                System.out.println("    Model: " + appConfig.getOllama().getModel());
                System.out.println("    URL: " + appConfig.getOllama().getUrl());
            } else if ("openai".equalsIgnoreCase(appConfig.getProvider()) && appConfig.getOpenai() != null) {
                System.out.println("    Model: " + appConfig.getOpenai().getModel());
                if (appConfig.getOpenai().getUrl() != null) {
                    System.out.println("    URL: " + appConfig.getOpenai().getUrl());
                }
            }
        } else {
            System.out.println("[X] No AI Provider configured!");
            System.out.println("\nQuick Setup Commands:");
            System.out.println("  secai config --provider ollama --url http://127.0.0.1:11434");
            System.out.println("  secai config --provider openai --api-key <YOUR_KEY>");
            System.out.println("  secai config --provider gemini --api-key <YOUR_KEY>");
        }

        System.out.println("\n--- Available Commands ---");
        System.out.println("  secai scan .         - Scan the current directory for vulnerabilities");
        System.out.println("  secai list           - List findings from the most recent scan");
        System.out.println("  secai explain <id>   - Get an AI explanation of a specific finding");
        System.out.println("  secai fix <id>       - Generate AI remediation for a finding");
        System.out.println("  secai chat           - Chat interactively with the AI assistant");
        System.out.println("  secai report         - Generate an HTML security report");
        System.out.println("  secai doctor         - Diagnose system health and tools");
        System.out.println("  secai update         - Update scanner rules and definitions");
        System.out.println("\nRun 'secai --help' for a full list of options.");

        java.util.Scanner scanner = new java.util.Scanner(System.in);
        while (true) {
            System.out.print("\n> ");
            if (!scanner.hasNextLine()) break;
            
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;
            
            if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit") || 
                input.equalsIgnoreCase("/exit") || input.equalsIgnoreCase("/quit")) {
                break;
            }
            
            // Remove optional leading slash
            if (input.startsWith("/")) {
                input = input.substring(1);
            }
            
            String[] argsArray = CommandParserUtils.parseArgs(input);
            if (argsArray.length > 0) {
                new CommandLine(this, factory).execute(argsArray);
            }
        }
        
        inRepl = false;
        return 0;
    }
}
