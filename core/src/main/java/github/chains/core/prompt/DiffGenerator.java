package github.chains.core.prompt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class to generate diff files between original and transformed Java files
 * using the system diff command (like Bacardi).
 */
public final class DiffGenerator {

    private DiffGenerator() {
        // utility class
    }

    /**
     * Generates a diff file between the original and transformed files using the system diff command.
     * The diff is written in unified format (-u) to the specified output file.
     *
     * @param originalFile the original file path
     * @param transformedFile the transformed file path
     * @param diffOutputFile the output file where the diff will be written
     * @throws IOException if file operations fail
     * @throws InterruptedException if the diff process is interrupted
     */
    public static void generateDiff(Path originalFile, Path transformedFile, Path diffOutputFile) 
            throws IOException, InterruptedException {
        if (!Files.exists(transformedFile)) {
            throw new IOException("Transformed file not found: " + transformedFile);
        }

        if (!Files.exists(originalFile)) {
            throw new IOException("Original file not found: " + originalFile);
        }

        Files.createDirectories(diffOutputFile.getParent());

        // Execute diff command: unified format (-u) with tab expansion (-t)
        // -u: unified format (standard, readable, compatible with git/patch)
        // -t: expand tabs to spaces for better readability
        // Note: Using -u -t instead of -w -t to capture all changes including formatting
        String command = String.format("diff -u -t %s %s",
                originalFile.toAbsolutePath().toString(),
                transformedFile.toAbsolutePath().toString());

        ProcessBuilder processBuilder;
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            // Windows - use cmd.exe
            processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
        } else {
            // Unix/Linux/Mac - use sh
            processBuilder = new ProcessBuilder("sh", "-c", command);
        }

        // Redirect output to diff file
        processBuilder.redirectOutput(diffOutputFile.toFile());
        processBuilder.redirectError(ProcessBuilder.Redirect.PIPE);

        Process process = processBuilder.start();

        // Read error stream in case of errors
        List<String> errorLines = new ArrayList<>();
        try (BufferedReader errorReader = new BufferedReader(
                new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = errorReader.readLine()) != null) {
                errorLines.add(line);
            }
        }

        int exitCode = process.waitFor();

        // diff returns 0 if files are identical, 1 if different, 2 if error
        if (exitCode == 2) {
            // Error occurred - write error message to diff file instead
            StringBuilder errorMsg = new StringBuilder();
            errorMsg.append("Error executing diff command. Exit code: ").append(exitCode).append("\n");
            for (String err : errorLines) {
                errorMsg.append(err).append("\n");
            }
            Files.writeString(diffOutputFile, errorMsg.toString(), StandardCharsets.UTF_8);
            throw new IOException("Error executing diff: " + String.join("\n", errorLines));
        } else if (exitCode == 0) {
            // Files are identical - diff file will be empty, write a message
            Files.writeString(diffOutputFile, "Files are identical.\n", StandardCharsets.UTF_8);
        }
        // exitCode == 1 means files differ, which is expected - diff file already contains the diff
    }
}

