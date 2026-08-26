package cli.Grep_2.Grepv1;

import java.io.*;
import java.util.*;

public class Grepv1 {

    public static void main(String[] args) throws Exception {
        String pattern = null;
        String target = null;
        String outputFile = null;
        boolean caseInsensitive = false;
        boolean recursive = false;
        boolean countOnly = false;
        int afterLines = 0;
        int beforeLines = 0;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("-i")) {
                caseInsensitive = true;
            } else if (a.equals("-r")) {
                recursive = true;
            } else if (a.equals("-C")) {
                countOnly = true;
            } else if (a.equals("-o")) {
                i++;
                outputFile = args[i];
            } else if (a.equals("-A")) {
                i++;
                afterLines = Integer.parseInt(args[i]);
            } else if (a.equals("-B")) {
                i++;
                beforeLines = Integer.parseInt(args[i]);
            } else {
                if (pattern == null) {
                    pattern = a;
                } else {
                    target = a;
                }
            }
        }

        if (pattern == null) {
            System.err.println("usage: ./mygrep <pattern> [file|dir] [-i] [-r] [-o out] [-A n] [-B n] [-C]");
            System.exit(2);
            return;
        }

        List<String> outputLines = new ArrayList<>();

        if (target == null) {
            BufferedReader stdinReader = new BufferedReader(new InputStreamReader(System.in));
            List<String> allLines = new ArrayList<>();
            String l;
            while ((l = stdinReader.readLine()) != null) {
                allLines.add(l);
            }

            int matchCount = 0;
            for (int idx = 0; idx < allLines.size(); idx++) {
                String line = allLines.get(idx);
                String hay = caseInsensitive ? line.toLowerCase() : line;
                String needle = caseInsensitive ? pattern.toLowerCase() : pattern;
                if (hay.contains(needle)) {
                    matchCount++;
                    if (!countOnly) {
                        for (int b = Math.max(0, idx - beforeLines); b < idx; b++) {
                            outputLines.add(allLines.get(b));
                        }
                        outputLines.add(line);
                        for (int af = idx + 1; af <= Math.min(allLines.size() - 1, idx + afterLines); af++) {
                            outputLines.add(allLines.get(af));
                        }
                    }
                }
            }
            if (countOnly) {
                outputLines.add(String.valueOf(matchCount));
            }

        } else {
            File targetFile = new File(target);

            if (targetFile.isDirectory()) {
                walkDirectory(targetFile, pattern, caseInsensitive, countOnly, afterLines, beforeLines, outputLines);
            } else {
                if (!targetFile.exists()) {
                    System.err.println("./mygrep: " + target + ": open: No such file or directory");
                    System.exit(1);
                    return;
                }
                if (!targetFile.canRead()) {
                    System.err.println("./mygrep: " + target + ": Permission denied");
                    System.exit(1);
                    return;
                }

                List<String> allLines = readAllLinesNaively(targetFile);

                int matchCount = 0;
                for (int idx = 0; idx < allLines.size(); idx++) {
                    String line = allLines.get(idx);
                    String hay = caseInsensitive ? line.toLowerCase() : line;
                    String needle = caseInsensitive ? pattern.toLowerCase() : pattern;
                    if (hay.contains(needle)) {
                        matchCount++;
                        if (!countOnly) {
                            for (int b = Math.max(0, idx - beforeLines); b < idx; b++) {
                                outputLines.add(allLines.get(b));
                            }
                            outputLines.add(line);
                            for (int af = idx + 1; af <= Math.min(allLines.size() - 1, idx + afterLines); af++) {
                                outputLines.add(allLines.get(af));
                            }
                        }
                    }
                }
                if (countOnly) {
                    outputLines.add(String.valueOf(matchCount));
                }
            }
        }

        if (outputFile != null) {
            File outFile = new File(outputFile);
            if (outFile.exists()) {
                System.err.println("./mygrep: " + outputFile + ": file already exists");
                System.exit(1);
                return;
            }
            PrintWriter writer = new PrintWriter(new FileWriter(outFile));
            for (String line : outputLines) {
                writer.println(line);
            }
            writer.close();
        } else {
            for (String line : outputLines) {
                System.out.println(line);
            }
        }
    }

    static void walkDirectory(File dir, String pattern, boolean caseInsensitive, boolean countOnly,
                               int afterLines, int beforeLines, List<String> outputLines) throws IOException {
        File[] entries = dir.listFiles();
        if (entries == null) {
            return;
        }

        for (File entry : entries) {
            if (entry.isDirectory()) {
                walkDirectory(entry, pattern, caseInsensitive, countOnly, afterLines, beforeLines, outputLines);
            } else {
                List<String> allLines = readAllLinesNaively(entry);
                int matchCount = 0;
                List<String> fileOutput = new ArrayList<>();
                for (int idx = 0; idx < allLines.size(); idx++) {
                    String line = allLines.get(idx);
                    String hay = caseInsensitive ? line.toLowerCase() : line;
                    String needle = caseInsensitive ? pattern.toLowerCase() : pattern;
                    if (hay.contains(needle)) {
                        matchCount++;
                        if (!countOnly) {
                            for (int b = Math.max(0, idx - beforeLines); b < idx; b++) {
                                fileOutput.add(entry.getPath() + ":" + allLines.get(b));
                            }
                            fileOutput.add(entry.getPath() + ":" + line);
                            for (int af = idx + 1; af <= Math.min(allLines.size() - 1, idx + afterLines); af++) {
                                fileOutput.add(entry.getPath() + ":" + allLines.get(af));
                            }
                        }
                    }
                }
                if (countOnly) {
                    if (matchCount > 0) {
                        outputLines.add(entry.getPath() + ":" + matchCount);
                    }
                } else if (!fileOutput.isEmpty()) {
                    outputLines.addAll(fileOutput);
                }
            }
        }
    }

    static List<String> readAllLinesNaively(File file) throws IOException {
        List<String> lines = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line;
        while ((line = reader.readLine()) != null) {
            lines.add(line);
        }
        reader.close();
        return lines;
    }
}
