package com.secai.cli;

import com.secai.ai.AIEngine;
import com.secai.model.Finding;
import com.secai.report.ReportManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

@Component
@Command(name = "fix", description = "Get remediation advice and a secure code example for a finding.")
public class FixCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "The ID of the finding to fix (e.g. 1)")
    private String findingId;
    
    @Option(names = {"-p", "--path"}, description = "Project path", defaultValue = ".")
    private String projectPath;
    
    @Option(names = {"--apply"}, description = "Write the secure code example to a .fixed file")
    private boolean apply;

    private final AIEngine aiEngine;
    private final ReportManager reportManager;

    @Autowired
    public FixCommand(AIEngine aiEngine, ReportManager reportManager) {
        this.aiEngine = aiEngine;
        this.reportManager = reportManager;
    }

    @Override
    public Integer call() {
        System.out.println("Loading findings for project at: " + projectPath);
        
        List<Finding> findings = reportManager.loadLatestScan(projectPath);
        if (findings.isEmpty()) {
            return 1;
        }
        
        Optional<Finding> findingOpt = findings.stream()
                .filter(f -> f.getId().equals(findingId))
                .findFirst();
                
        if (findingOpt.isEmpty()) {
            System.out.println("Error: No finding found with ID " + findingId);
            return 1;
        }
        
        Finding finding = findingOpt.get();
        System.out.println("\nGenerating Fix for Finding [" + finding.getId() + "]: " + finding.getTitle() + "...\n");
        
        if (finding.getFile() != null && !finding.getFile().isEmpty()) {
            try {
                java.nio.file.Path targetPath = java.nio.file.Paths.get(projectPath, finding.getFile());
                if (java.nio.file.Files.exists(targetPath)) {
                    List<String> allLines = java.nio.file.Files.readAllLines(targetPath);
                    int lineIndex = finding.getLine() > 0 ? finding.getLine() - 1 : 0;
                    int startLine = Math.max(0, lineIndex - 15);
                    int endLine = Math.min(allLines.size(), lineIndex + 15);
                    
                    StringBuilder contextBuilder = new StringBuilder();
                    for (int i = startLine; i < endLine; i++) {
                        contextBuilder.append(allLines.get(i)).append("\n");
                    }
                    finding.setFileContext(contextBuilder.toString());
                }
            } catch (Exception e) {
                System.out.println("Warning: Could not read context for file " + finding.getFile());
            }
        }
        
        aiEngine.analyzeFindings(List.of(finding));
        
        System.out.println("\n--- AI Remediation ---");
        System.out.println(com.secai.util.MarkdownRenderer.render(finding.getAiRemediation()));
        System.out.println("\n--- Secure Code Example ---");
        System.out.println(com.secai.util.MarkdownRenderer.render(finding.getSecureCodeExample()));
        
        if (finding.getSearchString() != null && finding.getReplaceString() != null) {
            System.out.println("\n--- Proposed Code Changes ---");
            System.out.println("[-]: " + finding.getSearchString().replace("\n", "\n[-]: "));
            System.out.println("[+]: " + finding.getReplaceString().replace("\n", "\n[+]: "));

            java.io.Console console = System.console();
            boolean shouldApply = apply;
            
            if (!shouldApply && console != null) {
                String input = console.readLine("\nApply this fix to " + finding.getFile() + "? [y/N]: ");
                if ("y".equalsIgnoreCase(input) || "yes".equalsIgnoreCase(input)) {
                    shouldApply = true;
                } else {
                    System.out.println("Fix discarded.");
                }
            } else if (!shouldApply) {
                System.out.println("\n(Run interactively or use --apply to apply this fix automatically)");
            }
            
            if (shouldApply) {
                try {
                    java.nio.file.Path targetPath = java.nio.file.Paths.get(projectPath, finding.getFile());
                    String content = java.nio.file.Files.readString(targetPath);
                    if (content.contains(finding.getSearchString())) {
                        content = content.replace(finding.getSearchString(), finding.getReplaceString());
                        java.nio.file.Files.writeString(targetPath, content);
                        System.out.println("\n[Self-Healing] Successfully patched: " + targetPath);
                    } else {
                        System.out.println("\n[Self-Healing] Error: Could not find the exact vulnerable code block in the file to replace.");
                    }
                } catch (Exception e) {
                    System.out.println("\n[Self-Healing] Error modifying file: " + e.getMessage());
                }
            }
        }
        
        return 0;
    }
}
