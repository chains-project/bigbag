package github.chains.core.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility class for file system operations.
 */
public final class FileSystemUtils {

    private FileSystemUtils() {
        // Utility class
    }

    /**
     * Deletes a directory and all its contents recursively.
     *
     * @param directory the directory to delete
     * @throws IOException if deletion fails
     */
    public static void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        if (Files.isDirectory(directory)) {
            try (var stream = Files.newDirectoryStream(directory)) {
                for (Path entry : stream) {
                    deleteDirectory(entry);
                }
            }
        }

        Files.delete(directory);
    }
}

