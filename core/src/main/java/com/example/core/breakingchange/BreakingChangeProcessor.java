package com.example.core.breakingchange;

import com.example.core.model.BreakingUpdateRecord;
import com.example.core.pipeline.FailureCategoryUtils;
import com.example.core.pipeline.ProjectLogLocator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import github.chains.breakingclassifier.BreakingClassifierApp;
import github.chains.breakingclassifier.BreakingReport;
import github.chains.breakingclassifier.FileErrorGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.DockerBuild;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * High-level service that reads a BreakingUpdateRecord, extracts the associated Docker image,
 * locates the Maven log and runs the breaking-classifier to obtain the failure category.
 */
public class BreakingChangeProcessor {

    private static final Logger log = LoggerFactory.getLogger(BreakingChangeProcessor.class);

    private final DockerBuild dockerBuild;
    private final ObjectMapper objectMapper;
    private final BreakingClassifierApp classifierApp;

    public BreakingChangeProcessor(DockerBuild dockerBuild) {
        this(dockerBuild, createObjectMapper());
    }

    public BreakingChangeProcessor(DockerBuild dockerBuild, ObjectMapper objectMapper) {
        this.dockerBuild = dockerBuild;
        this.objectMapper = objectMapper;
        this.classifierApp = new BreakingClassifierApp();
    }

    /**
     * Executes the full extraction + classification flow for the BreakingChange JSON located at {@code jsonFile}.
     *
     * @param jsonFile       path to the JSON definition
     * @param outputBaseDir  directory where the project should be extracted
     * @param forceExtraction whether the extraction should overwrite existing content
     * @return processing result with classifier information
     */
    public BreakingChangeProcessingResult process(Path jsonFile, Path outputBaseDir, boolean forceExtraction) {
        try {
            BreakingUpdateRecord record = objectMapper.readValue(jsonFile.toFile(), BreakingUpdateRecord.class);
            return process(record, outputBaseDir, forceExtraction);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read BreakingChange JSON file: " + jsonFile, e);
        }
    }

    /**
     * Serializes the processing result to JSON at the provided path, creating parent directories if needed.
     *
     * @param result     result payload
     * @param targetJson destination file
     */
    public void writeResult(BreakingChangeProcessingResult result, Path targetJson) {
        try {
            if (targetJson.getParent() != null) {
                Files.createDirectories(targetJson.getParent());
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(targetJson.toFile(), result);
            log.info("Saved classification result to {}", targetJson);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write result JSON to " + targetJson, e);
        }
    }

    private BreakingChangeProcessingResult process(BreakingUpdateRecord record, Path outputBaseDir, boolean forceExtraction) {
        validateRecord(record);

        String dockerImage = dockerBuild.extractDockerImageFromCommand(record.breakingUpdateReproductionCommand());
        if (dockerImage == null || dockerImage.isBlank()) {
            throw new IllegalStateException("Could not resolve Docker image from breakingUpdateReproductionCommand");
        }

        String containerProjectPath = FailureCategoryUtils.normalizeContainerProjectPath(record.project());
        Path extractionDir = dockerBuild.extractProjectAndM2FromImage(
                dockerImage,
                containerProjectPath,
                outputBaseDir,
                record.breakingCommit(),
                forceExtraction
        );

        Path projectDir = extractionDir;
        Path logFile = ProjectLogLocator.findLogFile(projectDir, record.project(), record.breakingCommit());
        if (logFile == null) {
            throw new IllegalStateException("No log file was found under " + projectDir);
        }

        BreakingReport report = runClassifier(logFile);

        Map<String, Integer> errorsPerFile = new HashMap<>();
        int totalErrors = 0;
        if (report != null && report.errorsByFile() != null) {
            for (FileErrorGroup group : report.errorsByFile()) {
                int count = group.errors() == null ? 0 : group.errors().size();
                errorsPerFile.put(group.filePath(), count);
                totalErrors += count;
            }
        }

        String failureCategory = report != null && report.failureCategory() != null
                ? report.failureCategory().name()
                : "UNKNOWN";

        return new BreakingChangeProcessingResult(
                record.project(),
                record.breakingCommit(),
                dockerImage,
                extractionDir.toAbsolutePath().toString(),
                logFile.toAbsolutePath().toString(),
                failureCategory,
                totalErrors,
                errorsPerFile.isEmpty() ? null : Map.copyOf(errorsPerFile),
                Instant.now()
        );
    }

    private BreakingReport runClassifier(Path logFile) {
        try {
            return classifierApp.analyzeLog(logFile);
        } catch (Exception e) {
            throw new IllegalStateException("breaking-classifier failed for log " + logFile, e);
        }
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    private static void validateRecord(BreakingUpdateRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("BreakingChange record cannot be null");
        }
        if (record.breakingCommit() == null || record.breakingCommit().isBlank()) {
            throw new IllegalArgumentException("BreakingChange is missing breakingCommit");
        }
        if (record.breakingUpdateReproductionCommand() == null || record.breakingUpdateReproductionCommand().isBlank()) {
            throw new IllegalArgumentException("BreakingChange is missing breakingUpdateReproductionCommand");
        }
    }
}

