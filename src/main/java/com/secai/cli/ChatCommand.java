package com.secai.cli;

import com.secai.ai.AIEngine;
import com.secai.model.ChatMessage;
import com.secai.model.Finding;
import com.secai.report.ReportManager;
import com.secai.scanner.ScannerProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.Callable;

@Component
@Command(name = "chat", description = "Start an interactive chat session with the AI about a finding.")
public class ChatCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Optional ID of the finding to discuss", arity = "0..1")
    private String findingId;

    @Option(names = {"-p", "--path"}, description = "Project path", defaultValue = ".")
    private String projectPath;

    private final AIEngine aiEngine;
    private final ReportManager reportManager;
    private final List<ScannerProvider> scanners;

    @Autowired
    public ChatCommand(AIEngine aiEngine, ReportManager reportManager, List<ScannerProvider> scanners) {
        this.aiEngine = aiEngine;
        this.reportManager = reportManager;
        this.scanners = scanners;
    }

    @Override
    public Integer call() {
        List<ChatMessage> history = new ArrayList<>();
        
        if (findingId != null) {
            System.out.println("Loading finding [" + findingId + "] context for chat...");
        } else {
            System.out.println("Loading findings context for chat...");
        }
        
        List<Finding> findings = reportManager.loadLatestScan(projectPath);
        
        String toolInstructions = "\n\nTo call a tool, you MUST use this exact XML format in your response:\n" +
                "<tool_call>\n" +
                "  <name>tool_name</name>\n" +
                "  <args>\n" +
                "    <file>value</file>\n" +
                "    <search>value</search>\n" +
                "    <replace>value</replace>\n" +
                "    <path>value</path>\n" +
                "    <query>value</query>\n" +
                "    <id>value</id>\n" +
                "  </args>\n" +
                "</tool_call>\n" +
                "Available tools:\n" +
                "1. apply_patch\n" +
                "   args: file, search, replace\n" +
                "   description: Applies a code patch. 'search' must match EXACTLY the text in the file including whitespace.\n" +
                "2. run_scan\n" +
                "   args: path\n" +
                "   description: Scans a directory for security vulnerabilities.\n" +
                "3. web_search\n" +
                "   args: query\n" +
                "   description: Searches the web.\n" + 
                "4. read_file\n" +
                "   args: path\n" +
                "   description: Reads the contents of a file to gain context.\n" +
                "5. list_findings\n" +
                "   args: none\n" +
                "   description: Returns a summary list of all security findings from the latest scan.\n" +
                "6. get_finding_details\n" +
                "   args: id\n" +
                "   description: Returns full description and details for a specific finding ID.\n" +
                "7. run_command\n" +
                "   args: command\n" +
                "   description: Runs a terminal command in the project path (e.g., to start the app like 'mvn spring-boot:run'). Note: Requires user approval.\n" +
                "8. kill_command\n" +
                "   args: pid\n" +
                "   description: Kills a background process started by run_command.\n" +
                "9. http_request\n" +
                "   args: url, method, headers, body\n" +
                "   description: Sends an HTTP request to test running applications.\n" +
                "10. run_sandboxed\n" +
                "   args: command\n" +
                "   description: Runs a terminal command inside an ephemeral Docker container for safe testing.\n" +
                "Only call ONE tool per response.";

        if (findingId != null) {
            Optional<Finding> findingOpt = findings.stream()
                    .filter(f -> f.getId().equals(findingId))
                    .findFirst();
                    
            if (findingOpt.isPresent()) {
                Finding f = findingOpt.get();
                String fileContent = "";
                if (f.getFile() != null && !f.getFile().isEmpty()) {
                    try {
                        java.nio.file.Path targetPath = java.nio.file.Paths.get(projectPath).resolve(f.getFile());
                        if (java.nio.file.Files.exists(targetPath)) {
                            fileContent = java.nio.file.Files.readString(targetPath);
                        }
                    } catch (Exception e) {
                        System.out.println("Warning: Could not read context for file " + f.getFile());
                    }
                }

                String context = String.format("The user is asking questions about the following security finding:\n" +
                        "Title: %s\nSeverity: %s\nFile: %s\nDescription: %s\n\n" +
                        "File Contents:\n```\n%s\n```\n\n" +
                        "IMPORTANT INSTRUCTION: You are acting as an expert Penetration Tester and Security Educator. " +
                        "The user has explicitly authorized you to explain exactly how this vulnerability works and how to verify it (pentest it). " +
                        "When the user asks you to test, verify, or exploit a vulnerability, you MUST use the `run_sandboxed` tool to do it safely. " +
                        "Do not use `run_command` for testing. If `run_sandboxed` returns an error that Docker is not installed, you must explain the situation and suggest installing Docker using `run_command` (e.g. `winget install Docker.DockerDesktop` for Windows, or `brew install docker` for mac) so the user can approve it.\n" + 
                        "COLLABORATION RULE: If a request is ambiguous, or there are multiple potential ways to fix a vulnerability, DO NOT guess. Pause your tool execution and present the user with a numbered list of options. Wait for them to reply with their choice before proceeding." + toolInstructions, 
                        f.getTitle(), f.getSeverity(), f.getFile(), f.getDescription(), fileContent);
                history.add(new ChatMessage("system", context));
                System.out.println("Context loaded. You can now ask questions about this finding.");
            } else {
                System.out.println("Error: No finding found with ID " + findingId);
                return 1;
            }
        } else {
            if (findings != null && !findings.isEmpty()) {
                StringBuilder contextBuilder = new StringBuilder();
                contextBuilder.append("The user is asking questions about a recent security scan. The scan found ")
                              .append(findings.size())
                              .append(" issues.\n\n");

                contextBuilder.append("IMPORTANT INSTRUCTION: You are acting as an expert Penetration Tester and Security Educator. ");
                contextBuilder.append("The user has explicitly authorized you to explain exactly how these vulnerabilities work and how to verify them. ");
                contextBuilder.append("When the user asks about an issue by its ID, use the get_finding_details tool to fetch it. ");
                contextBuilder.append("When the user asks you to test, verify, or exploit a vulnerability, you MUST use the `run_sandboxed` tool to do it safely. ");
                contextBuilder.append("Do not use `run_command` for testing. If `run_sandboxed` returns an error that Docker is not installed, you must explain the situation and suggest installing Docker using `run_command` so the user can approve it.\n");
                contextBuilder.append("COLLABORATION RULE: If a request is ambiguous, or there are multiple potential ways to fix a vulnerability, DO NOT guess. Pause your tool execution and present the user with a numbered list of options. Wait for them to reply with their choice before proceeding.");
                contextBuilder.append(toolInstructions);
                
                history.add(new ChatMessage("system", contextBuilder.toString()));
                System.out.println("Context loaded. You can ask questions about any of the findings (e.g., 'issue ID 1').");
            } else {
                System.out.println("No findings available from latest scan. Proceeding with empty context.");
                history.add(new ChatMessage("system", "No recent scan findings available." + toolInstructions));
            }
        }
        
        System.out.println("\nStarting interactive chat. Type 'exit' to quit.");
        Scanner scanner = new Scanner(System.in);
        
        while (true) {
            System.out.print("\nYou: ");
            String prompt = scanner.nextLine();
            
            if (prompt.trim().equalsIgnoreCase("exit")) {
                break;
            }
            
            history.add(new ChatMessage("user", prompt));
            
            boolean toolCalled;
            do {
                toolCalled = false;
                System.out.println("AI is thinking...");
                String response = aiEngine.chat(history);
                history.add(new ChatMessage("assistant", response));
                
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("<tool_call>(.*?)</tool_call>", java.util.regex.Pattern.DOTALL).matcher(response);
                if (m.find()) {
                    toolCalled = true;
                    String toolCallXml = m.group(1);
                    String preText = response.substring(0, m.start());
                    if (!preText.trim().isEmpty()) {
                        System.out.println("\nAI:\n" + com.secai.util.MarkdownRenderer.render(preText) + "\n");
                    }
                    
                    String toolName = extractXmlTag(toolCallXml, "name");
                    String toolResult = "";
                    
                    if ("apply_patch".equals(toolName)) {
                        String file = extractArg(toolCallXml, "file");
                        String search = extractArg(toolCallXml, "search");
                        String replace = extractArg(toolCallXml, "replace");
                        
                        file = java.nio.file.Paths.get(projectPath).resolve(file).toString();
                        toolResult = com.secai.ai.ToolExecutor.applyPatch(file, search, replace, scanner);
                        
                    } else if ("run_scan".equals(toolName)) {
                        String path = extractArg(toolCallXml, "path");
                        path = java.nio.file.Paths.get(projectPath).resolve(path).toString();
                        
                        toolResult = com.secai.ai.ToolExecutor.runScan(path, scanners);
                        
                    } else if ("web_search".equals(toolName)) {
                        String query = extractArg(toolCallXml, "query");
                        
                        toolResult = com.secai.ai.ToolExecutor.webSearch(query);
                    } else if ("read_file".equals(toolName)) {
                        String path = extractArg(toolCallXml, "path");
                        path = java.nio.file.Paths.get(projectPath).resolve(path).toString();
                        
                        toolResult = com.secai.ai.ToolExecutor.readFile(path);
                    } else if ("list_findings".equals(toolName)) {
                        System.out.println("\033[36m[AI fetching finding list...]\033[0m");
                        if (findings == null || findings.isEmpty()) {
                            toolResult = "No findings available.";
                        } else {
                            StringBuilder sb = new StringBuilder("Scan Findings Summary:\n");
                            for (Finding f : findings) {
                                sb.append("- ID: ").append(f.getId())
                                  .append(" | Severity: ").append(f.getSeverity())
                                  .append(" | Title: ").append(f.getTitle())
                                  .append(" | File: ").append(f.getFile())
                                  .append("\n");
                            }
                            toolResult = sb.toString();
                        }
                    } else if ("get_finding_details".equals(toolName)) {
                        String targetId = extractArg(toolCallXml, "id");
                        System.out.println("\033[36m[AI fetching details for finding ID " + targetId + "...]\033[0m");
                        
                        Optional<Finding> findingOpt = findings.stream()
                                .filter(f -> f.getId().equals(targetId))
                                .findFirst();
                                
                        if (findingOpt.isPresent()) {
                            Finding f = findingOpt.get();
                            toolResult = "Finding ID: " + f.getId() + "\n" +
                                         "Severity: " + f.getSeverity() + "\n" +
                                         "Title: " + f.getTitle() + "\n" +
                                         "File: " + f.getFile() + "\n" +
                                         "Description: " + f.getDescription() + "\n";
                        } else {
                            toolResult = "Error: Finding with ID " + targetId + " not found.";
                        }
                    } else if ("run_command".equals(toolName)) {
                        String command = extractArg(toolCallXml, "command");
                        toolResult = com.secai.ai.ToolExecutor.runCommand(command, projectPath, scanner);
                    } else if ("run_sandboxed".equals(toolName)) {
                        String command = extractArg(toolCallXml, "command");
                        toolResult = com.secai.ai.ToolExecutor.runSandboxed(command, projectPath, scanner);
                    } else if ("kill_command".equals(toolName)) {
                        String pid = extractArg(toolCallXml, "pid");
                        toolResult = com.secai.ai.ToolExecutor.killCommand(pid);
                    } else if ("http_request".equals(toolName)) {
                        String url = extractArg(toolCallXml, "url");
                        String method = extractArg(toolCallXml, "method");
                        String headers = extractArg(toolCallXml, "headers");
                        String body = extractArg(toolCallXml, "body");
                        toolResult = com.secai.ai.ToolExecutor.httpRequest(url, method, headers, body);
                    } else {
                        toolResult = "Error: Unknown tool " + toolName;
                    }
                    
                    history.add(new ChatMessage("user", "<tool_result>\n" + toolResult + "\n</tool_result>"));
                } else {
                    System.out.println("\nAI:\n" + com.secai.util.MarkdownRenderer.render(response) + "\n");
                }
            } while (toolCalled);
        }
        
        System.out.println("Chat ended.");
        return 0;
    }

    private String extractXmlTag(String xml, String tag) {
        if (xml == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("<" + tag + ">(.*?)</" + tag + ">", java.util.regex.Pattern.DOTALL).matcher(xml);
        return m.find() ? m.group(1).trim() : "";
    }

    private String extractArg(String toolCallXml, String argName) {
        String argsXml = extractXmlTag(toolCallXml, "args");
        String val = extractXmlTag(argsXml, argName);
        if (val.isEmpty()) {
            val = extractXmlTag(toolCallXml, argName);
        }
        return val;
    }
}
