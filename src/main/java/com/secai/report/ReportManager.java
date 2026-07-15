package com.secai.report;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secai.model.Finding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import com.secai.model.Report;
import com.secai.model.ChatMessage;

@Service
@RegisterReflectionForBinding({Finding.class, Report.class, ChatMessage.class})
public class ReportManager {
    private static final Logger logger = LoggerFactory.getLogger(ReportManager.class);
    private static final String STATE_DIR = ".secai";
    private static final String LATEST_SCAN_FILE = "latest-scan.json";

    private final ObjectMapper mapper;

    public ReportManager() {
        this.mapper = new ObjectMapper();
    }

    public void saveLatestScan(String projectPath, List<Finding> findings) {
        try {
            Path dirPath = Paths.get(projectPath, STATE_DIR);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }
            File outputFile = dirPath.resolve(LATEST_SCAN_FILE).toFile();
            mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, findings);
            logger.debug("Saved {} findings to {}", findings.size(), outputFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to save latest scan state: {}", e.getMessage());
        }
    }

    public List<Finding> loadLatestScan(String projectPath) {
        File inputFile = Paths.get(projectPath, STATE_DIR, LATEST_SCAN_FILE).toFile();
        if (!inputFile.exists()) {
            logger.warn("No previous scan found at {}. Please run 'secai scan' first.", inputFile.getAbsolutePath());
            return new ArrayList<>();
        }
        
        try {
            return mapper.readValue(inputFile, new TypeReference<List<Finding>>() {});
        } catch (IOException e) {
            logger.error("Failed to load previous scan state: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
