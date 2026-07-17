package com.secai.ai;

import com.secai.model.Finding;
import com.secai.scanner.ScannerProvider;
import com.secai.util.DiffRenderer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ToolExecutor {

    public static String applyPatch(String filePath, String search, String replace, Scanner scanner) {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            return "Error: File " + filePath + " does not exist.";
        }
        
        try {
            String content = Files.readString(path);
            
            // Normalize line endings for comparison
            String normalizedContent = content.replace("\r\n", "\n");
            String normalizedSearch = search.replace("\r\n", "\n");
            String normalizedReplace = replace.replace("\r\n", "\n");

            if (!normalizedContent.contains(normalizedSearch)) {
                return "Error: The exact search string was not found in the file. AI, please try again with the exact contents of the file.";
            }

            DiffRenderer.printDiff(filePath, normalizedSearch, normalizedReplace);
            System.out.print("\033[36mApply this patch to " + filePath + "? [y/N]: \033[0m");
            String answer = scanner.nextLine().trim().toLowerCase();

            if (answer.equals("y") || answer.equals("yes")) {
                String newContent = normalizedContent.replace(normalizedSearch, normalizedReplace);
                Files.writeString(path, newContent);
                System.out.println("\033[32mPatch applied successfully.\033[0m");
                return "Success: Patch was approved and applied to " + filePath + ".";
            } else {
                System.out.println("\033[31mPatch rejected.\033[0m");
                return "User rejected the patch. Ask the user what needs to be changed.";
            }

        } catch (IOException e) {
            return "Error reading or writing file: " + e.getMessage();
        }
    }

    public static String runScan(String targetPath, List<ScannerProvider> scanners) {
        Path path = Paths.get(targetPath);
        if (!Files.exists(path)) {
            return "Error: Path " + targetPath + " does not exist.";
        }
        
        System.out.println("\033[36m[AI running security scan on " + targetPath + " ...]\033[0m");
        StringBuilder report = new StringBuilder("Scan Results:\n");
        int totalFindings = 0;

        for (ScannerProvider scanner : scanners) {
            try {
                List<Finding> findings = scanner.scan(targetPath);
                for (Finding f : findings) {
                    report.append("- [").append(f.getSeverity()).append("] ").append(f.getTitle())
                          .append(" in ").append(f.getFile()).append("\n");
                    totalFindings++;
                }
            } catch (Exception e) {
                report.append("- Error running ").append(scanner.getName()).append(": ").append(e.getMessage()).append("\n");
            }
        }
        
        if (totalFindings == 0) {
            return "Scan completed. No vulnerabilities found.";
        }
        return report.toString();
    }

    public static String webSearch(String query) {
        System.out.println("\033[36m[AI searching web for: " + query + " ...]\033[0m");
        try {
            String url = "https://html.duckduckgo.com/html/?q=" + java.net.URLEncoder.encode(query, "UTF-8");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build();
            
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            String html = response.body();
            
            Matcher m = Pattern.compile("class=\"result__snippet[^\"]*\"[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE).matcher(html);
            StringBuilder sb = new StringBuilder();
            int count = 0;
            while (m.find() && count < 5) {
                String snippet = m.group(1).replaceAll("<[^>]+>", "");
                sb.append("- ").append(snippet).append("\n");
                count++;
            }
            
            if (sb.length() == 0) return "No results found.";
            return "Web Search Results:\n" + sb.toString();
        } catch (Exception e) {
            return "Web search failed: " + e.getMessage();
        }
    }

    public static String readFile(String targetPath) {
        Path path = Paths.get(targetPath);
        if (!Files.exists(path)) {
            return "Error: File " + targetPath + " does not exist.";
        }
        System.out.println("\033[36m[AI reading file: " + targetPath + " ...]\033[0m");
        try {
            String content = Files.readString(path);
            return "File Contents of " + targetPath + ":\n```\n" + content + "\n```";
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }
}
