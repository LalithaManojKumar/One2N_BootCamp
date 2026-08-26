package cli.WordCount_1.WCv2;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


public final class wordCount {

    public static void main(String[] args) {
        WcOptions options;
        try {
            options = ArgumentParser.parse(args);
        } catch (ArgumentParser.ArgumentException e) {
            System.err.println("./wc: " + e.getMessage());
            System.exit(2);
            return;
        }

        List<WcResult> results = runCounts(options);
        boolean hadError = printResults(results, options);

        System.exit(hadError ? 1 : 0);
    }

    private static List<WcResult> runCounts(WcOptions options) {
        if (options.readsFromStdin()) {
            return List.of(WcService.process(new StdinInputSource(System.in)));
        }
        List<InputSource> sources = options.files().stream()
                .<InputSource>map(FileInputSource::new)
                .toList();
        return WcService.processAll(sources);
    }

    private static boolean printResults(List<WcResult> results, WcOptions options) {
        boolean hadError = false;
        for (WcResult result : results) {
            if (result.isError()) {
                System.err.println(OutputFormatter.formatError(result));
                hadError = true;
            } else {
                System.out.println(OutputFormatter.formatResult(result, options));
            }
        }
        if (options.needsTotalRow()) {
            System.out.println(OutputFormatter.formatResult(WcService.total(results), options));
        }
        return hadError;
    }


    record WcOptions(boolean showLines, boolean showWords, boolean showChars, List<String> files) {

        WcOptions {
            files = List.copyOf(files);
        }

        boolean readsFromStdin() {
            return files.isEmpty();
        }

        boolean needsTotalRow() {
            return files.size() > 1;
        }
    }


    record WcResult(String label, long lines, long words, long bytes, Optional<String> error) {

        static WcResult ok(String label, long lines, long words, long bytes) {
            return new WcResult(label, lines, words, bytes, Optional.empty());
        }

        static WcResult failed(String label, String errorMessage) {
            return new WcResult(label, 0, 0, 0, Optional.of(errorMessage));
        }

        boolean isError() {
            return error.isPresent();
        }

        WcResult plus(WcResult other) {
            return new WcResult("total", lines + other.lines, words + other.words, bytes + other.bytes, Optional.empty());
        }
    }


    static final class ArgumentParser {

        private ArgumentParser() {
        }

        static WcOptions parse(String[] args) {
            boolean lines = false;
            boolean words = false;
            boolean chars = false;
            List<String> files = new ArrayList<>();

            for (String arg : args) {
                if (arg.length() > 1 && arg.charAt(0) == '-') {
                    for (int i = 1; i < arg.length(); i++) {
                        switch (arg.charAt(i)) {
                            case 'l' -> lines = true;
                            case 'w' -> words = true;
                            case 'c' -> chars = true;
                            default -> throw new ArgumentException("invalid option -- '" + arg.charAt(i) + "'");
                        }
                    }
                } else {
                    files.add(arg);
                }
            }

            if (!lines && !words && !chars) {
                lines = true;
                words = true;
                chars = true;
            }

            return new WcOptions(lines, words, chars, files);
        }

        static final class ArgumentException extends RuntimeException {
            ArgumentException(String message) {
                super(message);
            }
        }
    }


    interface InputSource {
        String label();

        InputStream open() throws InputSourceException;

        final class InputSourceException extends IOException {
            InputSourceException(String message) {
                super(message);
            }
        }
    }

    static final class FileInputSource implements InputSource {
        private final Path path;

        FileInputSource(String fileName) {
            this.path = Path.of(fileName);
        }

        @Override
        public String label() {
            return path.toString();
        }

        @Override
        public InputStream open() throws InputSourceException {
            if (Files.notExists(path)) {
                throw new InputSourceException("open: No such file or directory");
            }
            if (Files.isDirectory(path)) {
                throw new InputSourceException("read: Is a directory");
            }
            if (!Files.isReadable(path)) {
                throw new InputSourceException("open: Permission denied");
            }
            try {
                return new BufferedInputStream(Files.newInputStream(path));
            } catch (IOException e) {
                throw new InputSourceException("open: " + e.getMessage());
            }
        }
    }

    static final class StdinInputSource implements InputSource {
        private final InputStream stdin;

        StdinInputSource(InputStream stdin) {
            this.stdin = stdin;
        }

        @Override
        public String label() {
            return "";
        }

        @Override
        public InputStream open() {
            return stdin;
        }
    }


    record Counts(long lines, long words, long bytes) {
    }

    static final class Counter {

        private static final int BUFFER_SIZE = 64 * 1024;

        private Counter() {
        }

        static Counts count(InputStream input) throws IOException {
            byte[] buffer = new byte[BUFFER_SIZE];
            long lines = 0;
            long words = 0;
            long bytes = 0;
            boolean inWord = false;

            int read;
            while ((read = input.read(buffer)) != -1) {
                for (int i = 0; i < read; i++) {
                    byte b = buffer[i];
                    bytes++;
                    if (b == '\n') {
                        lines++;
                    }
                    if (isWhitespace(b)) {
                        inWord = false;
                    } else if (!inWord) {
                        inWord = true;
                        words++;
                    }
                }
            }
            return new Counts(lines, words, bytes);
        }

        private static boolean isWhitespace(byte b) {
            return b == ' ' || b == '\t' || b == '\n' || b == '\r' || b == '\f' || b == 0x0B;
        }
    }


    static final class WcService {

        private static final int MAX_CONCURRENCY = Math.max(2, Runtime.getRuntime().availableProcessors() * 2);

        private WcService() {
        }

        static WcResult process(InputSource source) {
            try (InputStream in = source.open()) {
                Counts c = Counter.count(in);
                return WcResult.ok(source.label(), c.lines(), c.words(), c.bytes());
            } catch (InputSource.InputSourceException e) {
                return WcResult.failed(source.label(), e.getMessage());
            } catch (IOException e) {
                return WcResult.failed(source.label(), "read: " + e.getMessage());
            }
        }

        static List<WcResult> processAll(List<? extends InputSource> sources) {
            if (sources.size() == 1) {
                return List.of(process(sources.get(0)));
            }

            int poolSize = Math.min(MAX_CONCURRENCY, sources.size());
            ExecutorService pool = Executors.newFixedThreadPool(poolSize);
            try {
                List<Callable<WcResult>> tasks = sources.stream()
                        .<Callable<WcResult>>map(source -> () -> process(source))
                        .toList();

                List<Future<WcResult>> futures = pool.invokeAll(tasks);
                return futures.stream().map(f -> {
                    try {
                        return f.get();
                    } catch (ExecutionException | InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("counting task failed unexpectedly", e);
                    }
                }).toList();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("counting was interrupted", e);
            } finally {
                pool.shutdown();
            }
        }

        static WcResult total(List<WcResult> results) {
            WcResult sum = WcResult.ok("total", 0, 0, 0);
            for (WcResult r : results) {
                if (!r.isError()) {
                    sum = sum.plus(r);
                }
            }
            return sum;
        }
    }

    static final class OutputFormatter {

        private static final String COLUMN_FORMAT = "%8d";

        private OutputFormatter() {
        }

        static String formatResult(WcResult result, WcOptions options) {
            StringBuilder sb = new StringBuilder();
            appendColumn(sb, options.showLines(), result.lines());
            appendColumn(sb, options.showWords(), result.words());
            appendColumn(sb, options.showChars(), result.bytes());
            if (!result.label().isEmpty()) {
                sb.append(' ').append(result.label());
            }
            return sb.toString();
        }

        static String formatError(WcResult result) {
            return "./wc: " + result.label() + ": " + result.error().orElse("unknown error");
        }

        private static void appendColumn(StringBuilder sb, boolean enabled, long value) {
            if (!enabled) {
                return;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(String.format(COLUMN_FORMAT, value));
        }
    }
}
