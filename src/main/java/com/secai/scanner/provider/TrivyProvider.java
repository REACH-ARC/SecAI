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
public class TrivyProvider implements ScannerProvider {
    private static final Logger logger = LoggerFactory.getLogger(TrivyProvider.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public List<Finding> scan(String projectPath) {
        List<Finding> findings = new ArrayList<>();
        File outputFile = new File(projectPath, "trivy-results.json");

        try {
            // Run trivy command: trivy fs --format json --output trivy-results.json <projectPath>
            ProcessBuilder pb = new ProcessBuilder("trivy", "fs", "--format", "json", "--output", outputFile.getAbsolutePath(), projectPath);
            pb.directory(new File(projectPath));
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process process = pb.start();
            
            int exitCode = process.waitFor();
            
            if (outputFile.exists()) {
                // Read bytes and convert to string, silently replacing malformed characters to avoid UTF-8 parse errors
                byte[] fileBytes = java.nio.file.Files.readAllBytes(outputFile.toPath());
                String jsonContent = new String(fileBytes, java.nio.charset.StandardCharsets.UTF_8);
                JsonNode rootNode = mapper.readTree(jsonContent);
                JsonNode results = rootNode.path("Results");
                
                if (results.isArray()) {
                    for (JsonNode targetNode : results) {
                        String target = targetNode.path("Target").asText();
                        JsonNode vulnerabilities = targetNode.path("Vulnerabilities");
                        
                        if (vulnerabilities.isArray()) {
                            for (JsonNode vulnNode : vulnerabilities) {
                                Finding finding = new Finding();
                                finding.setId(UUID.randomUUID().toString());
                                finding.setScannerName(getName());
                                finding.setTitle(vulnNode.path("VulnerabilityID").asText() + " in " + vulnNode.path("PkgName").asText());
                                finding.setDescription(vulnNode.path("Title").asText() + " - " + vulnNode.path("Description").asText());
                                finding.setSeverity(vulnNode.path("Severity").asText());
                                finding.setFile(target);
                                
                                findings.add(finding);
                            }
                        }
                    }
                }
                
                // Cleanup temp file
                outputFile.delete();
            } else {
                logger.warn("Trivy finished with exit code {} but no output file was found.", exitCode);
            }
        } catch (IOException e) {
            logger.error("Error executing Trivy: {}", e.getMessage());
        } catch (InterruptedException e) {
            logger.error("Trivy execution interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }

        return findings;
    }

    @Override
    public void updateRules() {
        System.out.println("Trivy: Updating vulnerability database...");
        try {
            Process process = new ProcessBuilder("trivy", "image", "--download-db-only")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            process.waitFor();
            System.out.println("Trivy: Database update complete.");
        } catch (Exception e) {
            System.out.println("Trivy: Failed to update database: " + e.getMessage());
        }
    }

    @Override
    public String getName() {
        return "Trivy";
    }

    @Override
    public boolean isAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("trivy", "--version");
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            logger.warn("Trivy is not available: {}", e.getMessage());
            return false;
        }
    }
}
