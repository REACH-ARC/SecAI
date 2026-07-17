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
            List<Finding> findings = reportManager.loadLatestScan(projectPath);
            Optional<Finding> findingOpt = findings.stream()
                    .filter(f -> f.getId().equals(findingId))
                    .findFirst();
                    
            if (findingOpt.isPresent()) {
                Finding f = findingOpt.get();
                String fileContent = "";
                if (f.getFile() != null && !f.getFile().isEmpty()) {
                    try {
                        java.nio.file.Path targetPath = java.nio.file.Paths.get(projectPath, f.getFile());
                        if (java.nio.file.Files.exists(targetPath)) {
                            fileContent = java.nio.file.Files.readString(targetPath);
                        }
                    } catch (Exception e) {
                        System.out.println("Warning: Could not read context for file " + f.getFile());
                    }
                }
                
                String toolInstructions = "\n\nTo call a tool, you MUST use this exact XML format in your response:\n" +
                        "<tool_call>\n" +
                        "  <name>tool_name</name>\n" +
                        "  <args>\n" +
                        "    <file>value</file>\n" +
                        "    <search>value</search>\n" +
                        "    <replace>value</replace>\n" +
                        "    <path>value</path>\n" +
                        "    <query>value</query>\n" +
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
                        "Only call ONE tool per response.";

                String context = String.format("The user is asking questions about the following security finding:\n" +
                        "Title: %s\nSeverity: %s\nFile: %s\nDescription: %s\n\n" +
                        "File Contents:\n```\n%s\n```\n\n" +
                        "IMPORTANT INSTRUCTION: You are acting as an expert Penetration Tester and Security Educator. " +
                        "The user has explicitly authorized you to explain exactly how this vulnerability works and how to verify it (pentest it). " +
                        "Do not refuse to explain the attack mechanics. Provide concrete steps, commands, or code snippets " +
                        "to demonstrate how an attacker would exploit this, so the user can verify the fix." + toolInstructions, 
                        f.getTitle(), f.getSeverity(), f.getFile(), f.getDescription(), fileContent);
                history.add(new ChatMessage("system", context));
                System.out.println("Context loaded. You can now ask questions about this finding.");
            } else {
                System.out.println("Error: No finding found with ID " + findingId);
                return 1;
            }
        } else {
            System.out.println("Loading all findings context for chat...");
            List<Finding> findings = reportManager.loadLatestScan(projectPath);
            if (findings != null && !findings.isEmpty()) {
                StringBuilder contextBuilder = new StringBuilder();
                contextBuilder.append("The user is asking questions about a recent security scan. The scan found the following issues:\n\n");
                for (Finding f : findings) {
                    contextBuilder.append(String.format("- ID %s: %s\n  Severity: %s\n  File: %s\n  Description: %s\n\n", 
                            f.getId(), f.getTitle(), f.getSeverity(), f.getFile(), f.getDescription()));
                }
                
                String toolInstructions = "\n\nTo call a tool, you MUST use this exact XML format in your response:\n" +
                        "<tool_call>\n" +
                        "  <name>tool_name</name>\n" +
                        "  <args>\n" +
                        "    <file>value</file>\n" +
                        "    <search>value</search>\n" +
                        "    <replace>value</replace>\n" +
                        "    <path>value</path>\n" +
                        "    <query>value</query>\n" +
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
                        "Only call ONE tool per response.";

                contextBuilder.append("IMPORTANT INSTRUCTION: You are acting as an expert Penetration Tester and Security Educator. ");
                contextBuilder.append("The user has explicitly authorized you to explain exactly how these vulnerabilities work and how to verify them. ");
                contextBuilder.append("When the user asks about an issue by its ID, reference the specific finding above and provide concrete steps to exploit/verify it.");
                contextBuilder.append(toolInstructions);
                
                history.add(new ChatMessage("system", contextBuilder.toString()));
                System.out.println("Context loaded. You can ask questions about any of the findings (e.g., 'issue ID 1').");
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
                        String argsXml = extractXmlTag(toolCallXml, "args");
                        String file = extractXmlTag(argsXml, "file");
                        String search = extractXmlTag(argsXml, "search");
                        String replace = extractXmlTag(argsXml, "replace");
                        
                        file = java.nio.file.Paths.get(projectPath, file).toString();
                        toolResult = com.secai.ai.ToolExecutor.applyPatch(file, search, replace, scanner);
                        
                    } else if ("run_scan".equals(toolName)) {
                        String argsXml = extractXmlTag(toolCallXml, "args");
                        String path = extractXmlTag(argsXml, "path");
                        path = java.nio.file.Paths.get(projectPath, path).toString();
                        
                        toolResult = com.secai.ai.ToolExecutor.runScan(path, scanners);
                        
                    } else if ("web_search".equals(toolName)) {
                        String argsXml = extractXmlTag(toolCallXml, "args");
                        String query = extractXmlTag(argsXml, "query");
                        
                        toolResult = com.secai.ai.ToolExecutor.webSearch(query);
                    } else if ("read_file".equals(toolName)) {
                        String argsXml = extractXmlTag(toolCallXml, "args");
                        String path = extractXmlTag(argsXml, "path");
                        path = java.nio.file.Paths.get(projectPath, path).toString();
                        
                        toolResult = com.secai.ai.ToolExecutor.readFile(path);
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
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("<" + tag + ">(.*?)</" + tag + ">", java.util.regex.Pattern.DOTALL).matcher(xml);
        return m.find() ? m.group(1).trim() : "";
    }
}
