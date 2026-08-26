package cli.WordCount_1.WCv1;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class wordCount {

    public static void main(String[] args) throws Exception {
        boolean lFlag = false;
        boolean wFlag = false;
        boolean cFlag = false;
        List<String> files = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("-")) {
                for (int j = 1; j < a.length(); j++) {
                    char ch = a.charAt(j);
                    switch (ch) {
                        case 'l':
                            lFlag = true;
                            break;
                        case 'w':
                            wFlag = true;
                            break;
                        case 'c':
                            cFlag = true;
                            break;
                        default:
                            System.err.println("./wc: invalid option -- '" + ch + "'");
                            System.exit(2);
                    }
                }
            } else {
                files.add(a);
            }
        }

        if (!lFlag && !wFlag && !cFlag) {
            lFlag = true;
            wFlag = true;
            cFlag = true;
        }

        int exitCode = 0;

        if (files.isEmpty()) {

            byte[] data = readAllStdin();
            long lines = countLines(data);
            long words = countWords(data);
            long chars = data.length;

            StringBuilder sb = new StringBuilder();
            if (lFlag) sb.append(String.format("%8d", lines));
            if (wFlag) sb.append(String.format("%8d", words));
            if (cFlag) sb.append(String.format("%8d", chars));
            System.out.println(sb.toString());
            System.exit(0);
        }

        long totalLines = 0;
        long totalWords = 0;
        long totalChars = 0;

        for (int i = 0; i < files.size(); i++) {
            String fileName = files.get(i);
            File f = new File(fileName);

            if (!f.exists()) {
                System.err.println("./wc: " + fileName + ": open: No such file or directory");
                exitCode = 1;
                continue;
            }
            if (f.isDirectory()) {
                System.err.println("./wc: " + fileName + ": read: Is a directory");
                exitCode = 1;
                continue;
            }
            if (!f.canRead()) {
                System.err.println("./wc: " + fileName + ": open: Permission denied");
                exitCode = 1;
                continue;
            }

            byte[] data;
            try {
                data = Files.readAllBytes(f.toPath());
            } catch (IOException e) {
                System.err.println("./wc: " + fileName + ": " + e.getMessage());
                exitCode = 1;
                continue;
            }

            long lines = countLines(data);
            long words = countWords(data);
            long chars = data.length;

            totalLines += lines;
            totalWords += words;
            totalChars += chars;

            StringBuilder sb = new StringBuilder();
            if (lFlag) sb.append(String.format("%8d", lines));
            if (wFlag) sb.append(String.format("%8d", words));
            if (cFlag) sb.append(String.format("%8d", chars));
            sb.append(" ").append(fileName);
            System.out.println(sb.toString());
        }

        if (files.size() > 1) {
            StringBuilder sb = new StringBuilder();
            if (lFlag) sb.append(String.format("%8d", totalLines));
            if (wFlag) sb.append(String.format("%8d", totalWords));
            if (cFlag) sb.append(String.format("%8d", totalChars));
            sb.append(" total");
            System.out.println(sb.toString());
        }

        System.exit(exitCode);
    }

    private static byte[] readAllStdin() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = System.in.read(chunk)) != -1) {
            buffer.write(chunk, 0, n);
        }
        return buffer.toByteArray();
    }

    private static long countLines(byte[] data) {
        long count = 0;
        for (byte b : data) {
            if (b == '\n') count++;
        }
        return count;
    }

    private static long countWords(byte[] data) {
        String text = new String(data);
        String[] parts = text.trim().split("\\s+");
        if (parts.length == 1 && parts[0].isEmpty()) return 0;
        return parts.length;
    }
}
