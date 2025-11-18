package github.chains.breakingclassifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class FailureCategorizer {

    private static final Map<Pattern, FailureCategory> FAILURE_PATTERNS = new LinkedHashMap<>();

    static {
        // Order matters! Patterns are evaluated in order, and the first match wins.
        // COMPILATION_FAILURE should be LAST as it's the most generic pattern.
        
        // 1. Java version incompatibility - specific pattern, checked first
        // Pattern matches messages like:
        // - "bad class file: ... class file version X.X"
        // - "class file has wrong version X.X, should be X.X"
        FAILURE_PATTERNS.put(
                Pattern.compile("(?i)(bad class file:.*?(?:class file version|wrong version)|"
                        + "class file has wrong version (\\d+\\.\\d+), should be (\\d+\\.\\d+))"),
                FailureCategory.JAVA_VERSION_FAILURE);
        
        // 2. Test failures
        FAILURE_PATTERNS.put(
                Pattern.compile("(?i)(\\[ERROR] Tests run:|There are test failures|There were test failures|"
                        + "Failed to execute goal org\\.apache\\.maven\\.plugins:maven-surefire-plugin)"),
                FailureCategory.TEST_FAILURE);
        
        // 3. Werror failures
        FAILURE_PATTERNS.put(
                Pattern.compile("(?i)(warnings found and -Werror specified)"),
                FailureCategory.WERROR_FAILURE);
        
        // 4. Enforcer failures
        FAILURE_PATTERNS.put(
                Pattern.compile("(?i)(Failed to execute goal org\\.apache\\.maven\\.plugins:maven-enforcer-plugin|"
                        + "Failed to execute goal org\\.jenkins-ci\\.tools:maven-hpi-plugin)"),
                FailureCategory.ENFORCER_FAILURE);
        
        // 5. Dependency resolution failures
        FAILURE_PATTERNS.put(
                Pattern.compile("(?i)(Could not resolve dependencies|\\[ERROR] Some problems were encountered while processing the POMs|"
                        + "\\[ERROR] .*?The following artifacts could not be resolved)"),
                FailureCategory.DEPENDENCY_RESOLUTION_FAILURE);
        
        // 6. Dependency lock failures
        FAILURE_PATTERNS.put(
                Pattern.compile("(?i)(Failed to execute goal se\\.vandmo:dependency-lock-maven-plugin:.*?:check)"),
                FailureCategory.DEPENDENCY_LOCK_FAILURE);
        
        // 7. Build success (not a failure, but included for completeness)
        FAILURE_PATTERNS.put(
                Pattern.compile("(?i)(BUILD SUCCESS)"),
                FailureCategory.BUILD_SUCCESS);
        
        // 8. COMPILATION_FAILURE - LAST as fallback for generic compilation errors
        // This is the most generic pattern and should only match if no specific pattern matched
        FAILURE_PATTERNS.put(
                Pattern.compile("(?i)(COMPILATION ERROR|Failed to execute goal io\\.takari\\.maven\\.plugins:takari-lifecycle-plugin.*?:compile)|Exit code: COMPILATION_ERROR"),
                FailureCategory.COMPILATION_FAILURE);
    }

    private FailureCategorizer() {
    }

    static FailureCategory categorize(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return FailureCategory.UNKNOWN;
        }
        String content = String.join("\n", lines);
        for (Map.Entry<Pattern, FailureCategory> entry : FAILURE_PATTERNS.entrySet()) {
            if (entry.getKey().matcher(content).find()) {
                System.out.println("[FailureCategorizer] Pattern matched: " + entry.getValue() + 
                        " (pattern: " + entry.getKey().pattern() + ")");
                return entry.getValue();
            }
        }
        System.out.println("[FailureCategorizer] No pattern matched, returning UNKNOWN");
        return FailureCategory.UNKNOWN;
    }
}

