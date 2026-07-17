package com.secai.util;

public class DiffRenderer {
    private static final String RESET = "\033[0m";
    // 24-bit ANSI colors
    private static final String BG_RED = "\033[48;2;70;20;20m";
    private static final String BG_GREEN = "\033[48;2;20;70;20m";
    private static final String TEXT_WHITE = "\033[38;2;220;220;220m";
    private static final String HEADER_COLOR = "\033[38;2;150;150;150m";

    public static void printDiff(String filePath, String searchString, String replaceString) {
        System.out.println("\n" + HEADER_COLOR + "╭─ Patching: " + filePath + RESET);
        
        String[] searchLines = searchString.split("\n", -1);
        String[] replaceLines = replaceString.split("\n", -1);

        // Print removed lines (red background)
        for (String line : searchLines) {
            if (!line.isEmpty() || searchLines.length > 1) {
                System.out.println(BG_RED + TEXT_WHITE + "- " + padRight(line, 80) + RESET);
            }
        }

        // Print added lines (green background)
        for (String line : replaceLines) {
            if (!line.isEmpty() || replaceLines.length > 1) {
                System.out.println(BG_GREEN + TEXT_WHITE + "+ " + padRight(line, 80) + RESET);
            }
        }
        
        System.out.println(HEADER_COLOR + "╰" + "─".repeat(50) + RESET + "\n");
    }

    private static String padRight(String s, int n) {
        if (s.length() >= n) {
            return s;
        }
        return String.format("%-" + n + "s", s);
    }
}
