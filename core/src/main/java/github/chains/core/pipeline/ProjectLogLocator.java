package github.chains.core.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility class responsible for finding build log files inside an extracted project directory.
 */
public final class ProjectLogLocator {

    private static final Logger log = LoggerFactory.getLogger(ProjectLogLocator.class);

    private ProjectLogLocator() {
        // Utility class
    }

    /**
     * Finds a log file for the given project and commit. It looks into {@code projectDir/<projectName>}
     * attempting several well-known filenames and finally falls back to any {@code *.log} file it can find.
     *
     * @param projectDir     path to the extracted {@code project/} directory
     * @param projectName    logical project name (may be {@code null})
     * @param breakingCommit breaking commit hash to match file names such as {@code <hash>.log}
     * @return the log file path or {@code null} if nothing could be located
     */
    public static Path findLogFile(Path projectDir, String projectName, String breakingCommit) {
        if (!Files.exists(projectDir) || !Files.isDirectory(projectDir)) {
            return null;
        }

        Path projectSubDir = resolveProjectSubDirectory(projectDir, projectName);
        Path logFile = findLogInsideDirectory(projectSubDir, breakingCommit);
        if (logFile != null) {
            return logFile;
        }

        return findLogInsideDirectory(projectDir, breakingCommit);
    }

    private static Path resolveProjectSubDirectory(Path projectDir, String projectName) {
        if (projectName != null && !projectName.isBlank()) {
            Path projectSubDir = projectDir.resolve(projectName);
            if (Files.exists(projectSubDir) && Files.isDirectory(projectSubDir)) {
                return projectSubDir;
            }
        }

        try {
            return Files.list(projectDir)
                    .filter(Files::isDirectory)
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            log.warn("Error searching for subdirectories under {}", projectDir, e);
            return null;
        }
    }

    private static Path findLogInsideDirectory(Path directory, String breakingCommit) {
        if (directory == null || !Files.exists(directory)) {
            return null;
        }

        String[] candidates = {
                breakingCommit + ".log",
                "build.log",
                "maven.log",
                "mavenLog.log",
                "output.log"
        };

        for (String candidate : candidates) {
            Path candidatePath = directory.resolve(candidate);
            if (Files.exists(candidatePath)) {
                return candidatePath;
            }
        }

        try {
            return Files.list(directory)
                    .filter(path -> path.toString().endsWith(".log"))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            log.warn("Error while listing log files in {}", directory, e);
            return null;
        }
    }
}

