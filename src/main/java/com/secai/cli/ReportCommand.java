package com.secai.cli;

import com.secai.ai.AIEngine;
import com.secai.model.ChatMessage;
import com.secai.model.Finding;
import com.secai.report.ReportManager;
import com.secai.report.exporter.ReportExporter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Component
@Command(name = "report", description = "Generate and export a security report.")
public class ReportCommand implements Callable<Integer> {

    @Option(names = {"-f", "--format"}, description = "Format to export (markdown, html)", defaultValue = "markdown")
    private String format;

    @Option(names = {"-o", "--output"}, description = "Output file path (optional)")
    private String outputPath;
    
    @Option(names = {"-p", "--path"}, description = "Project path to read scan from", defaultValue = ".")
    private String projectPath;
    
    @Option(names = {"--ai"}, description = "Use AI to generate a comprehensive penetration testing report")
    private boolean useAi;

    private final ReportManager reportManager;
    private final List<ReportExporter> exporters;
    private final AIEngine aiEngine;

    @Autowired
    public ReportCommand(ReportManager reportManager, List<ReportExporter> exporters, AIEngine aiEngine) {
        this.reportManager = reportManager;
        this.exporters = exporters;
        this.aiEngine = aiEngine;
    }

    @Override
    public Integer call() {
        System.out.println("Generating security report in format: " + format);
        
        List<Finding> findings = reportManager.loadLatestScan(projectPath);
        if (findings.isEmpty()) {
            return 1;
        }

        if (useAi) {
            System.out.println("\033[36m[AI generating comprehensive penetration testing report...]\033[0m");
            
            StringBuilder findingsSummary = new StringBuilder();
            for (Finding f : findings) {
                findingsSummary.append("- [").append(f.getSeverity()).append("] ")
                               .append(f.getTitle()).append(" in ").append(f.getFile()).append("\n");
            }
            
            String prompt = "You are a senior penetration tester. Generate a formal Penetration Testing Report in Markdown based on the following automated scan findings. Include an Executive Summary, Methodology, Detailed Findings, and Remediation Strategies.\n\nFindings:\n" + findingsSummary.toString();
            
            List<ChatMessage> history = new ArrayList<>();
            history.add(new ChatMessage("user", prompt));
            
            String aiReportContent = aiEngine.chat(history);
            
            String finalOutputPath = outputPath;
            if (finalOutputPath == null || finalOutputPath.isEmpty()) {
                finalOutputPath = Paths.get(projectPath, "secai-pentest-report.md").toString();
            }
            
            try {
                Path outPath = Paths.get(finalOutputPath);
                if (outPath.getParent() != null && !Files.exists(outPath.getParent())) {
                    Files.createDirectories(outPath.getParent());
                }
                Files.writeString(outPath, aiReportContent);
                System.out.println("AI Penetration Testing Report successfully generated: " + outPath.toAbsolutePath());
            } catch (IOException e) {
                System.out.println("Error writing report to file: " + e.getMessage());
                return 1;
            }
            return 0;
        }

        ReportExporter selectedExporter = null;
        for (ReportExporter exporter : exporters) {
            if (exporter.getClass().getSimpleName().toLowerCase().startsWith(format.toLowerCase())) {
                selectedExporter = exporter;
                break;
            }
        }

        if (selectedExporter == null) {
            System.out.println("Error: Unsupported format '" + format + "'. Use 'markdown' or 'html'.");
            return 1;
        }

        String reportContent = selectedExporter.export(findings);
        
        String finalOutputPath = outputPath;
        if (finalOutputPath == null || finalOutputPath.isEmpty()) {
            finalOutputPath = Paths.get(projectPath, "secai-report" + selectedExporter.getExtension()).toString();
        }

        try {
            Path outPath = Paths.get(finalOutputPath);
            if (outPath.getParent() != null && !Files.exists(outPath.getParent())) {
                Files.createDirectories(outPath.getParent());
            }
            Files.writeString(outPath, reportContent);
            System.out.println("Report successfully generated: " + outPath.toAbsolutePath());
        } catch (IOException e) {
            System.out.println("Error writing report to file: " + e.getMessage());
            return 1;
        }

        return 0;
    }
}

