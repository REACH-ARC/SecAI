package com.secai.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;
import java.util.ArrayList;

public class MarkdownRenderer {
    
    private static final String RESET = "\033[0m";
    private static final String BOLD = "\033[1m";
    private static final String ITALIC = "\033[3m";
    
    private static final String HEADER_COLOR = "\033[1;36m"; // Bold Cyan
    private static final String CODE_COLOR = "\033[33m"; // Yellow
    private static final String BLOCK_TEXT_COLOR = "\033[38;5;250m"; // Light Gray
    private static final String LIST_COLOR = "\033[1;32m"; // Bold Green
    private static final String LINK_TEXT = "\033[34m"; // Blue
    private static final String LINK_URL = "\033[4;34m"; // Underlined Blue
    
    public static String render(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return markdown;
        }

        // Sanitize typographic quotes and symbols for Windows terminal compatibility
        markdown = markdown.replace("’", "'")
                           .replace("‘", "'")
                           .replace("“", "\"")
                           .replace("”", "\"")
                           .replace("…", "...")
                           .replace("—", "--")
                           .replace("–", "-");

        // 1. Extract code blocks so we don't format inside them
        List<String> codeBlocks = new ArrayList<>();
        Pattern codeBlockPattern = Pattern.compile("```(.*?)\\n(.*?)\\n```", Pattern.DOTALL);
        Matcher matcher = codeBlockPattern.matcher(markdown);
        StringBuffer sb = new StringBuffer();
        int blockIndex = 0;
        
        while (matcher.find()) {
            String language = matcher.group(1).trim();
            String block = matcher.group(2);
            // Save as an array: language + code
            codeBlocks.add(language + "|||" + block);
            matcher.appendReplacement(sb, "@@@CODE_BLOCK_" + blockIndex + "@@@");
            blockIndex++;
        }
        matcher.appendTail(sb);
        String text = sb.toString();

        // 2. Format headers
        text = text.replaceAll("(?m)^(#{1,6})\\s+(.*)$", HEADER_COLOR + "$1 $2" + RESET);
        
        // 3. Format bold
        text = text.replaceAll("\\*\\*(.*?)\\*\\*", BOLD + "$1" + RESET);
        text = text.replaceAll("__(.*?)__", BOLD + "$1" + RESET);
        
        // 4. Format italic
        text = text.replaceAll("(?<!\\*)\\*(?!\\*)(.*?)(?<!\\*)\\*(?!\\*)", ITALIC + "$1" + RESET);
        
        // 5. Format inline code
        text = text.replaceAll("`([^`]+)`", CODE_COLOR + "$1" + RESET);
        
        // 6. Format lists (- item or * item)
        text = text.replaceAll("(?m)^(\\s*[-*])\\s+", LIST_COLOR + "$1" + RESET + " ");
        
        // 7. Format links
        text = text.replaceAll("\\[([^\\]]+)\\]\\(([^\\)]+)\\)", LINK_TEXT + "[$1]" + RESET + "(" + LINK_URL + "$2" + RESET + ")");

        // 8. Restore and format code blocks
        for (int i = 0; i < codeBlocks.size(); i++) {
            String[] parts = codeBlocks.get(i).split("\\|\\|\\|", 2);
            String language = parts[0];
            String block = parts.length > 1 ? parts[1] : "";
            
            StringBuilder formattedBlock = new StringBuilder();
            formattedBlock.append("\n").append(CODE_COLOR).append("╭─ ").append(language.isEmpty() ? "code" : language).append(" ").append("─".repeat(Math.max(2, 40 - language.length()))).append(RESET).append("\n");
            
            String[] lines = block.split("\n");
            for (String line : lines) {
                formattedBlock.append(CODE_COLOR).append("│ ").append(RESET).append(BLOCK_TEXT_COLOR).append(line).append(RESET).append("\n");
            }
            formattedBlock.append(CODE_COLOR).append("╰").append("─".repeat(44)).append(RESET).append("\n");
            
            text = text.replace("@@@CODE_BLOCK_" + i + "@@@", formattedBlock.toString());
        }

        return text;
    }
}
