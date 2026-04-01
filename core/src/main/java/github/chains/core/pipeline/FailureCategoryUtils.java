package github.chains.core.pipeline;

import github.chains.core.model.BreakingUpdateRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.models.FailureCategory;

/**
 * Utility class for working with FailureCategory.
 */
public class FailureCategoryUtils {

    private static final Logger log = LoggerFactory.getLogger(FailureCategoryUtils.class);

    /**
     * Checks if a breaking update record matches a failure category.
     *
     * @param record the breaking update record
     * @param category the failure category to match
     * @return true if the record matches the category, false otherwise
     */
    public static boolean matchesFailureCategory(BreakingUpdateRecord record, FailureCategory category) {
        if (record.failureCategory() == null) {
            return category == FailureCategory.UNKNOWN_FAILURE;
        }
        try {
            FailureCategory recordCategory = FailureCategory.valueOf(record.failureCategory().toUpperCase());
            return recordCategory == category;
        } catch (IllegalArgumentException e) {
            return category == FailureCategory.UNKNOWN_FAILURE;
        }
    }

    /**
     * Parses a failure category string to a FailureCategory enum.
     *
     * @param categoryStr the category string
     * @return the FailureCategory, or UNKNOWN_FAILURE if parsing fails
     */
    public static FailureCategory parseFailureCategory(String categoryStr) {
        if (categoryStr == null || categoryStr.trim().isEmpty()) {
            return FailureCategory.UNKNOWN_FAILURE;
        }
        try {
            return FailureCategory.valueOf(categoryStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid failure category: {}, defaulting to UNKNOWN_FAILURE", categoryStr);
            return FailureCategory.UNKNOWN_FAILURE;
        }
    }

    /**
     * Normalizes a project path for container mounting.
     * Ensures the path starts with "/" and defaults to "/project" if empty.
     *
     * @param projectPath the project path from the record
     * @return the normalized container path
     */
    public static String normalizeContainerProjectPath(String projectPath) {
        if (projectPath == null || projectPath.trim().isEmpty()) {
            return "/project";
        }
        return projectPath.startsWith("/") ? projectPath : "/" + projectPath;
    }
}

