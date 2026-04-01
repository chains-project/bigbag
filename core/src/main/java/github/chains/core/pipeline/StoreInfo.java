package github.chains.core.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Manages storage locations for pipeline artifacts.
 * Similar to Bacardi's StoreInfo, this class handles automatic path management
 * for storing prompts, responses, diffs, logs, and other artifacts.
 */
public class StoreInfo {

    private static final Logger log = LoggerFactory.getLogger(StoreInfo.class);

    private final SetupPipeline setupPipeline;
    private final Path patchFolder;
    private final boolean createNewPatchFolder;

    /**
     * Creates a new StoreInfo with a new patch folder.
     *
     * @param setupPipeline the pipeline setup
     * @param createNewPatchFolder if true, creates a new patch folder
     */
    public StoreInfo(SetupPipeline setupPipeline, boolean createNewPatchFolder) {
        this.setupPipeline = setupPipeline;
        this.createNewPatchFolder = createNewPatchFolder;
        if (createNewPatchFolder && setupPipeline.getOutPutPatchFolder() != null) {
            this.patchFolder = setupPipeline.getOutPutPatchFolder();
            try {
                Files.createDirectories(this.patchFolder);
            } catch (IOException e) {
                log.error("Failed to create patch folder: {}", this.patchFolder, e);
                throw new RuntimeException("Failed to create patch folder", e);
            }
        } else {
            this.patchFolder = setupPipeline.getOutPutPatchFolder();
        }
    }

    /**
     * Creates a StoreInfo reusing an existing patch folder.
     *
     * @param setupPipeline the pipeline setup
     * @param patchFolder the existing patch folder to reuse
     */
    public StoreInfo(SetupPipeline setupPipeline, Path patchFolder) {
        this.setupPipeline = setupPipeline;
        this.patchFolder = patchFolder;
        this.createNewPatchFolder = false;
    }

    /**
     * Gets the patch folder path.
     *
     * @return the patch folder path
     */
    public Path getPatchFolder() {
        return patchFolder;
    }

    /**
     * Copies content to a file in the patch folder structure.
     * Creates subdirectories as needed.
     *
     * @param relativePath the relative path from patch folder (e.g., "prompts/file.txt")
     * @param content the content to write
     * @return the path to the created file
     */
    public Path copyContentToFile(String relativePath, String content) throws IOException {
        Path targetFile = patchFolder.resolve(relativePath);
        Files.createDirectories(targetFile.getParent());
        Files.writeString(targetFile, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        log.debug("Wrote content to: {}", targetFile);
        return targetFile;
    }

    /**
     * Gets the path for prompts directory.
     *
     * @return the prompts directory path
     */
    public Path getPromptsDir() {
        return patchFolder.resolve("prompts");
    }

    /**
     * Gets the path for responses directory.
     *
     * @return the responses directory path
     */
    public Path getResponsesDir() {
        return patchFolder.resolve("responses");
    }

    /**
     * Gets the path for updated files directory.
     *
     * @return the updated files directory path
     */
    public Path getUpdatedDir() {
        return patchFolder.resolve("updated");
    }

    /**
     * Gets the path for original files directory.
     *
     * @return the original files directory path
     */
    public Path getOriginalDir() {
        return patchFolder.resolve("original");
    }

    /**
     * Gets the path for diffs directory.
     *
     * @return the diffs directory path
     */
    public Path getDiffsDir() {
        return patchFolder.resolve("diffs");
    }

    /**
     * Gets the path for breaking-classifier reports directory.
     *
     * @return the breaking-classifier reports directory path
     */
    public Path getBreakingClassifierReportsDir() {
        return patchFolder.resolve("breaking-classifier-reports");
    }

    /**
     * Gets the log file path from setup pipeline.
     *
     * @return the log file path
     */
    public Path getLogFilePath() {
        return setupPipeline.getLogFilePath();
    }

    /**
     * Gets the setup pipeline.
     *
     * @return the setup pipeline
     */
    public SetupPipeline getSetupPipeline() {
        return setupPipeline;
    }

    /**
     * Creates all standard subdirectories in the patch folder.
     */
    public void createSubdirectories() {
        try {
            Files.createDirectories(getPromptsDir());
            Files.createDirectories(getResponsesDir());
            Files.createDirectories(getUpdatedDir());
            Files.createDirectories(getOriginalDir());
            Files.createDirectories(getDiffsDir());
            Files.createDirectories(getBreakingClassifierReportsDir());
        } catch (IOException e) {
            log.error("Failed to create subdirectories in patch folder: {}", patchFolder, e);
            throw new RuntimeException("Failed to create subdirectories", e);
        }
    }
}

