package com.example.core.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Utility methods for locating the extracted project directory inside a breaking commit folder.
 */
public final class ProjectPaths {

    private ProjectPaths() {
        // Utility class
    }

    /**
     * Resolves the directory that contains the actual project sources inside the extracted breaking-commit folder.
     * Preference order:
     * 1. A subdirectory matching the provided project name.
     * 2. The first subdirectory (excluding "m2") inside the extraction folder.
     * 3. The extraction folder itself as a fallback.
     *
     * @param extractionDir the breaking commit directory
     * @param projectName the project name from the dataset (may be null/blank)
     * @return the resolved project directory
     */
    public static Path resolveProjectDir(Path extractionDir, String projectName) {
        if (extractionDir == null) {
            throw new IllegalArgumentException("Extraction directory cannot be null");
        }

        if (projectName != null && !projectName.isBlank()) {
            Path candidate = extractionDir.resolve(projectName);
            if (Files.exists(candidate)) {
                return candidate;
            }
        }

        try (Stream<Path> children = Files.list(extractionDir)) {
            return children
                    .filter(Files::isDirectory)
                    .filter(path -> !"m2".equals(path.getFileName().toString()))
                    .findFirst()
                    .orElse(extractionDir);
        } catch (IOException e) {
            return extractionDir;
        }
    }
}

