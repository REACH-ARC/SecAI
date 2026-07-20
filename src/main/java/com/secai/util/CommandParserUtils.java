package com.secai.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandParserUtils {
    public static String[] parseArgs(String commandLine) {
        List<String> list = new ArrayList<>();
        // Regex to match quoted strings or non-whitespace sequences
        Matcher m = Pattern.compile("([^\"]\\S*|\".+?\")\\s*").matcher(commandLine);
        while (m.find()) {
            String match = m.group(1);
            if (match.startsWith("\"") && match.endsWith("\"") && match.length() >= 2) {
                list.add(match.substring(1, match.length() - 1));
            } else {
                list.add(match);
            }
        }
        return list.toArray(new String[0]);
    }
}
