package com.secai.scanner.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secai.model.Finding;
import com.secai.scanner.ScannerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class SemgrepProvider implements ScannerProvider {
    private static final Logger logger = LoggerFactory.getLogger(SemgrepProvider.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public List<Finding> scan(String projectPath) {
        List<Finding> findings = new ArrayList<>();
        File outputFile = new File(projectPath, "semgrep-results.json");

        try {
            // Run semgrep command: semgrep scan --json -o semgrep-results.json <projectPath>
            ProcessBuilder pb = new ProcessBuilder("semgrep", "scan", "--json", "-o", outputFile.getAbsolutePath(), projectPath);
            pb.directory(new File(projectPath));
            Process process = pb.start();
            
            // Wait for completion (might take time depending on project size)
            int exitCode = process.waitFor();
            
            if (outputFile.exists()) {
                // Read file using default charset to avoid UTF-8 parse errors on Windows
                String jsonContent = java.nio.file.Files.readString(outputFile.toPath(), java.nio.charset.Charset.defaultCharset());
                JsonNode rootNode = mapper.readTree(jsonContent);
                JsonNode results = rootNode.path("results");
                
                if (results.isArray()) {
                    for (JsonNode node : results) {
                        Finding finding = new Finding();
                        finding.setId(UUID.randomUUID().toString());
                        finding.setScannerName(getName());
                        finding.setTitle(node.path("check_id").asText());
                        finding.setDescription(node.path("extra").path("message").asText());
                        finding.setSeverity(node.path("extra").path("severity").asText());
                        
                        JsonNode pathNode = node.path("path");
                        if (!pathNode.isMissingNode()) {
                            finding.setFile(pathNode.asText());
                        }
                        
                        JsonNode startNode = node.path("start");
                        if (!startNode.isMissingNode()) {
                            finding.setLine(startNode.path("line").asInt());
                        }
                        
                        findings.add(finding);
                    }
                }
                
                // Cleanup temp file
                outputFile.delete();
            } else {
                logger.warn("Semgrep finished with exit code {} but no output file was found.", exitCode);
            }
        } catch (IOException e) {
            logger.error("Error executing Semgrep: {}", e.getMessage());
        } catch (InterruptedException e) {
            logger.error("Semgrep execution interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }

        return findings;
    }

    @Override
    public void updateRules() {
        System.out.println("Semgrep: Rules are fetched dynamically during scan from the Semgrep Registry.");
        try {
            Process process = new ProcessBuilder("semgrep", "--version")
                    .redirectErrorStream(true)
                    .start();
            process.waitFor();
            System.out.println("Semgrep: Ready.");
        } catch (Exception e) {
            System.out.println("Semgrep: Failed to verify installation: " + e.getMessage());
        }
    }

    @Override
    public String getName() {
        return "Semgrep";
    }

    @Override
    public boolean isAvailable() {
        try {
            Process process = new ProcessBuilder("semgrep", "--version").start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
