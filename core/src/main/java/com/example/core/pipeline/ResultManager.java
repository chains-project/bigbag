package com.example.core.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.models.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages loading and saving of pipeline results.
 */
public class ResultManager {

    private static final Logger log = LoggerFactory.getLogger(ResultManager.class);

    private final Path resultsFilePath;
    private final ObjectMapper objectMapper;
    private final Map<String, Result> resultsMap;

    public ResultManager(Path resultsFilePath) {
        this.resultsFilePath = resultsFilePath;
        this.objectMapper = createObjectMapper();
        this.resultsMap = new ConcurrentHashMap<>();
        loadPreviousResults();
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * Loads previous results from the results file if it exists.
     */
    private void loadPreviousResults() {
        if (Files.exists(resultsFilePath)) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> rawResults = objectMapper.readValue(
                        resultsFilePath.toFile(), Map.class);
                
                log.info("Loaded {} previous results from {}", rawResults.size(), resultsFilePath);
                // Note: Results are stored by breakingCommit, we'll skip already processed ones
                // Full deserialization can be added if needed
            } catch (IOException e) {
                log.warn("Could not load previous results from {}", resultsFilePath, e);
            }
        }
    }

    /**
     * Saves all results to the results file.
     */
    public void saveResults() {
        try {
            Files.createDirectories(resultsFilePath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(resultsFilePath.toFile(), resultsMap);
            log.info("Saved {} results to {}", resultsMap.size(), resultsFilePath);
        } catch (IOException e) {
            log.error("Error saving results to {}", resultsFilePath, e);
        }
    }

    /**
     * Adds a result for a breaking commit.
     *
     * @param breakingCommit the breaking commit hash
     * @param result the result to store
     */
    public void addResult(String breakingCommit, Result result) {
        resultsMap.put(breakingCommit, result);
    }

    /**
     * Checks if a breaking commit has already been processed.
     *
     * @param breakingCommit the breaking commit hash
     * @return true if already processed, false otherwise
     */
    public boolean isProcessed(String breakingCommit) {
        return resultsMap.containsKey(breakingCommit);
    }

    /**
     * Gets the number of stored results.
     *
     * @return the number of results
     */
    public int getResultCount() {
        return resultsMap.size();
    }

    /**
     * Gets all results as a map.
     *
     * @return the results map
     */
    public Map<String, Result> getResultsMap() {
        return resultsMap;
    }
}

