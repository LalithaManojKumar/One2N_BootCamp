package cli.Grep_2.Grepv2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

public final class Grep {

    public static void main(String[] args) {
        Options options;
        try {
            options = parseArgs(args);
        } catch (ArgumentException e) {
            System.err.println("./mygrep: " + e.getMessage());
            System.exit(2);
            return;
        }

        List<String> matches;
        try {
            matches = collectMatches(options);
        } catch (GrepError e) {
            String label = options.target().orElse("(standard input)");
            System.err.println("./mygrep: " + label + ": " + e.getMessage());
            System.exit(1);
            return;
        }

        try {
            if (options.outputFile().isPresent()) {
                writeToFile(options.outputFile().get(), matches);
            } else {
                for (String line : matches) {
                    System.out.println(line);
                }
            }
            System.exit(0);
        } catch (GrepError e) {
            System.err.println("./mygrep: " + options.outputFile().get() + ": " + e.getMessage());
            System.exit(1);
        }
    }

    private static List<String> collectMatches(Options options) throws GrepError {
        if (options.target().isEmpty()) {
            return searchStdin(options);
        }
        Path target = Path.of(options.target().get());
        if (Files.isDirectory(target)) {
            return searchDirectory(options, target);
        }
        return searchFile(options, options.target().get());
    }

    record Options(String pattern, Optional<String> target, Optional<String> outputFile,
                    boolean caseInsensitive, int after, int before, boolean countOnly) {
    }

    static Options parseArgs(String[] args) {
        String pattern = null;
        String target = null;
        String outputFile = null;
        boolean caseInsensitive = false;
        int after = 0;
        int before = 0;
        boolean countOnly = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-o" -> {
                    if (i + 1 >= args.length) {
                        throw new ArgumentException("-o requires a filename");
                    }
                    outputFile = args[++i];
                }
                case "-i" -> caseInsensitive = true;
                case "-r" -> { }
                case "-A" -> {
                    if (i + 1 >= args.length) {
                        throw new ArgumentException("-A requires a number of lines");
                    }
                    after = parseNonNegativeInt(args[++i], "-A");
                }
                case "-B" -> {
                    if (i + 1 >= args.length) {
                        throw new ArgumentException("-B requires a number of lines");
                    }
                    before = parseNonNegativeInt(args[++i], "-B");
                }
                case "-C" -> countOnly = true;
                default -> {
                    if (pattern == null) {
                        pattern = arg;
                    } else if (target == null) {
                        target = arg;
                    } else {
                        throw new ArgumentException("unexpected argument: " + arg);
                    }
                }
            }
        }

        if (pattern == null) {
            throw new ArgumentException("missing search pattern");
        }
        return new Options(pattern, Optional.ofNullable(target), Optional.ofNullable(outputFile),
                caseInsensitive, after, before, countOnly);
    }

    private static int parseNonNegativeInt(String value, String flag) {
        try {
            int n = Integer.parseInt(value);
            if (n < 0) {
                throw new ArgumentException(flag + " requires a non-negative number, got: " + value);
            }
            return n;
        } catch (NumberFormatException e) {
            throw new ArgumentException(flag + " requires a number, got: " + value);
        }
    }

    static void writeToFile(String outputFile, List<String> matches) throws GrepError {
        Path path = Path.of(outputFile);
        if (Files.exists(path)) {
            throw new GrepError("file already exists");
        }
        try {
            Files.write(path, matches, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new GrepError("could not write file: " + e.getMessage());
        }
    }

    static List<String> searchFile(Options options, String fileName) throws GrepError {
        Path path = Path.of(fileName);
        if (Files.notExists(path)) {
            throw new GrepError("open: No such file or directory");
        }
        if (Files.isDirectory(path)) {
            throw new GrepError("read: Is a directory");
        }
        if (!Files.isReadable(path)) {
            throw new GrepError("Permission denied");
        }

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            return searchLines(reader, options);
        } catch (IOException e) {
            throw new GrepError("read: " + e.getMessage());
        }
    }

    static List<String> searchStdin(Options options) throws GrepError {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            return searchLines(reader, options);
        } catch (IOException e) {
            throw new GrepError("read: " + e.getMessage());
        }
    }

    private static final int MAX_CONCURRENCY = Math.max(2, Runtime.getRuntime().availableProcessors() * 2);

    static List<String> searchDirectory(Options options, Path dir) throws GrepError {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(dir)) {
            files = walk.filter(Files::isRegularFile).sorted(Comparator.naturalOrder()).toList();
        } catch (IOException e) {
            throw new GrepError("read: " + e.getMessage());
        }

        if (files.isEmpty()) {
            return List.of();
        }

        int poolSize = Math.min(MAX_CONCURRENCY, files.size());
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);
        List<FileOutcome> outcomes;
        try {
            List<Callable<FileOutcome>> tasks = files.stream()
                    .<Callable<FileOutcome>>map(file -> () -> searchOneFile(options, file))
                    .toList();

            List<Future<FileOutcome>> futures = pool.invokeAll(tasks);
            outcomes = new ArrayList<>();
            for (Future<FileOutcome> f : futures) {
                try {
                    outcomes.add(f.get());
                } catch (ExecutionException e) {
                    throw new IllegalStateException("search task failed unexpectedly", e.getCause());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("directory search was interrupted", e);
        } finally {
            pool.shutdown();
        }

        List<String> allMatches = new ArrayList<>();
        for (FileOutcome outcome : outcomes) {
            if (outcome.error().isPresent()) {
                System.err.println("./mygrep: " + outcome.file() + ": " + outcome.error().get());
                continue;
            }
            List<String> fileMatches = outcome.lines();
            if (options.countOnly()) {
                if (!fileMatches.get(0).equals("0")) {
                    allMatches.add(outcome.file() + ":" + fileMatches.get(0));
                }
            } else if (!fileMatches.isEmpty()) {
                for (String line : fileMatches) {
                    allMatches.add(line.equals("--") ? "--" : outcome.file() + ":" + line);
                }
            }
        }
        return allMatches;
    }

    private record FileOutcome(Path file, List<String> lines, Optional<String> error) {
        static FileOutcome ok(Path file, List<String> lines) {
            return new FileOutcome(file, lines, Optional.empty());
        }

        static FileOutcome failed(Path file, String message) {
            return new FileOutcome(file, List.of(), Optional.of(message));
        }
    }

    private static FileOutcome searchOneFile(Options options, Path file) {
        try {
            return FileOutcome.ok(file, searchFile(options, file.toString()));
        } catch (GrepError e) {
            return FileOutcome.failed(file, e.getMessage());
        }
    }


    private record NumberedLine(int index, String text) {
    }

    static List<String> searchLines(Reader source, Options options) throws IOException {
        BufferedReader reader = source instanceof BufferedReader br ? br : new BufferedReader(source);
        String needle = options.caseInsensitive() ? options.pattern().toLowerCase() : options.pattern();

        if (options.countOnly()) {
            return List.of(Long.toString(countMatches(reader, needle, options.caseInsensitive())));
        }

        List<String> out = new ArrayList<>();
        Deque<NumberedLine> beforeBuffer = new ArrayDeque<>();
        boolean contextRequested = options.after() > 0 || options.before() > 0;
        int afterRemaining = 0;
        int lastEmittedIndex = -1;
        int index = 0;

        String line;
        while ((line = reader.readLine()) != null) {
            String haystack = options.caseInsensitive() ? line.toLowerCase() : line;

            if (haystack.contains(needle)) {
                List<NumberedLine> toEmit = new ArrayList<>(beforeBuffer);
                toEmit.add(new NumberedLine(index, line));

                if (contextRequested && lastEmittedIndex != -1 && !toEmit.isEmpty()
                        && toEmit.get(0).index() > lastEmittedIndex + 1) {
                    out.add("--");
                }
                for (NumberedLine nl : toEmit) {
                    if (nl.index() > lastEmittedIndex) {
                        out.add(nl.text());
                        lastEmittedIndex = nl.index();
                    }
                }
                afterRemaining = options.after();
                beforeBuffer.clear();
            } else if (afterRemaining > 0) {
                if (index > lastEmittedIndex) {
                    out.add(line);
                    lastEmittedIndex = index;
                }
                afterRemaining--;
            } else if (options.before() > 0) {
                beforeBuffer.addLast(new NumberedLine(index, line));
                if (beforeBuffer.size() > options.before()) {
                    beforeBuffer.removeFirst();
                }
            }
            index++;
        }
        return out;
    }

    private static long countMatches(BufferedReader reader, String needle, boolean caseInsensitive) throws IOException {
        long count = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            String haystack = caseInsensitive ? line.toLowerCase() : line;
            if (haystack.contains(needle)) {
                count++;
            }
        }
        return count;
    }

    static final class GrepError extends Exception {
        GrepError(String message) {
            super(message);
        }
    }

    static final class ArgumentException extends RuntimeException {
        ArgumentException(String message) {
            super(message);
        }
    }
}
