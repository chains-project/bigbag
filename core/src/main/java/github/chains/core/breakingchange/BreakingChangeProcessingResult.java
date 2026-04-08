package github.chains.core.breakingchange;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BreakingChangeProcessingResult(
        String project,
        String breakingCommit,
        String dockerImage,
        String extractionDirectory,
        String logFile,
        String classifierCategory,
        int totalErrors,
        Map<String, Integer> errorsPerFile,
        Instant processedAt
) {
}

