package github.chains.breakingclassifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Maven build logs and extracts compiler errors associated with Java source files.
 */
public class MavenErrorExtractor {

    private static final Pattern ERROR_PATTERN = Pattern.compile(
            "^\\[ERROR\\] (?<file>.+?):\\[(?<line>\\d+)(?:,(?<column>\\d+))?\\] (?<message>.+)$");
    private static final String START_MARKER = "[WARNING] For more or less details";
    private static final String HELP_MARKER = "[ERROR] -> [Help 1]";

    public List<BreakingError> extract(Path logPath) throws IOException {
        Objects.requireNonNull(logPath, "logPath");
        if (!Files.exists(logPath)) {
            throw new IOException("Log file not found: " + logPath);
        }
        if (!Files.isRegularFile(logPath)) {
            throw new IOException("Log path is not a regular file: " + logPath);
        }

        try (BufferedReader reader = Files.newBufferedReader(logPath)) {
            return extract(reader);
        }
    }

    List<BreakingError> extract(BufferedReader reader) throws IOException {
        List<String> lines = new ArrayList<>();
        String rawLine;
        while ((rawLine = reader.readLine()) != null) {
            lines.add(rawLine.stripTrailing());
        }
        return extractFromLines(lines);
    }

    private List<BreakingError> extractFromLines(List<String> lines) {
        int startIndex = 0;
        boolean startFound = false;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(START_MARKER)) {
                startIndex = i + 1;
                startFound = true;
                break;
            }
        }

        if (startFound) {
            while (startIndex < lines.size() && lines.get(startIndex).startsWith("[WARNING]")) {
                startIndex++;
            }
        } else {
            startIndex = 0;
        }

        int endIndex = lines.size();
        for (int i = startIndex; i < lines.size(); i++) {
            if (lines.get(i).startsWith(HELP_MARKER)) {
                endIndex = i;
                break;
            }
        }

        if (startIndex >= lines.size()) {
            return List.of();
        }
        if (endIndex < startIndex) {
            endIndex = lines.size();
        }

        List<BreakingError> errors = new ArrayList<>();
        ErrorBuilder current = null;
        FailureCategory category = FailureCategorizer.categorize(lines);
        if (category == FailureCategory.UNKNOWN && startIndex < endIndex) {
            category = FailureCategorizer.categorize(lines.subList(startIndex, endIndex));
        }

        for (int i = startIndex; i < endIndex; i++) {
            String line = lines.get(i);

            if (line.startsWith(HELP_MARKER)) {
                if (current != null) {
                    errors.add(current.build());
                    current = null;
                }
                break;
            }

            Matcher matcher = ERROR_PATTERN.matcher(line);
            if (matcher.matches()) {
                if (current != null) {
                    errors.add(current.build());
                }
                current = new ErrorBuilder(
                        matcher.group("file"),
                        Integer.parseInt(matcher.group("line")),
                        matcher.group("column") != null ? Integer.parseInt(matcher.group("column")) : null,
                        matcher.group("message"),
                        category);
                continue;
            }

            if (current != null && line.startsWith("[ERROR]")) {
                String detail = line.substring("[ERROR]".length()).stripLeading();
                if (!detail.isBlank() && !detail.startsWith("->")) {
                    current.addDetail(detail);
                }
            }
        }

        if (current != null) {
            errors.add(current.build());
        }

        return errors;
    }

    private static final class ErrorBuilder {
        private final String filePath;
        private final int lineNumber;
        private final Integer columnNumber;
        private final String message;
        private final FailureCategory failureCategory;
        private final List<String> details = new ArrayList<>();

        private ErrorBuilder(String filePath, int lineNumber, Integer columnNumber, String message, FailureCategory failureCategory) {
            this.filePath = filePath;
            this.lineNumber = lineNumber;
            this.columnNumber = columnNumber;
            this.message = message;
            this.failureCategory = failureCategory;
        }

        private void addDetail(String detail) {
            details.add(detail);
        }

        private BreakingError build() {
            return new BreakingError(filePath, lineNumber, columnNumber, message, List.copyOf(details), failureCategory);
        }
    }
}

