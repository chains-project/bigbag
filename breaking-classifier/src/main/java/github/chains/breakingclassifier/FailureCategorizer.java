package github.chains.breakingclassifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class FailureCategorizer {

    private static final Map<Pattern, FailureCategory> FAILURE_PATTERNS = new LinkedHashMap<>();

    static {
        FAILURE_PATTERNS.put(
                Pattern.compile("(?i)(class file has wrong version (\\d+\\.\\d+), should be (\\d+\\.\\d+))"),
                FailureCategory.JAVA_VERSION_FAILURE);
        FAILURE_PATTERNS.put(
                Pattern.compile("(?i)(\\[ERROR] Tests run:|There are test failures|There were test failures|"
                        + "Failed to execute goal org\\.apache\\.maven\\.plugins:maven-surefire-plugin)"),
                FailureCategory.TEST_FAILURE);
        FAILURE_PATTERNS.put(
                Pattern.compile("(?i)(warnings found and -Werror specified)"),
                FailureCategory.WERROR_FAILURE);
        FAILURE_PATTERNS.put(
                Pattern.compile("(?i)(COMPILATION ERROR|Failed to execute goal io\\.takari\\.maven\\.plugins:takari-lifecycle-plugin.*?:compile)|Exit code: COMPILATION_ERROR"),
                FailureCategory.COMPILATION_FAILURE);
        FAILURE_PATTERNS.put(
                Pattern.compile("(?i)(BUILD SUCCESS)"),
                FailureCategory.BUILD_SUCCESS);
        FAILURE_PATTERNS.put(
                Pattern.compile("(?i)(Failed to execute goal org\\.apache\\.maven\\.plugins:maven-enforcer-plugin|"
                        + "Failed to execute goal org\\.jenkins-ci\\.tools:maven-hpi-plugin)"),
                FailureCategory.ENFORCER_FAILURE);
        FAILURE_PATTERNS.put(
                Pattern.compile("(?i)(Could not resolve dependencies|\\[ERROR] Some problems were encountered while processing the POMs|"
                        + "\\[ERROR] .*?The following artifacts could not be resolved)"),
                FailureCategory.DEPENDENCY_RESOLUTION_FAILURE);
        FAILURE_PATTERNS.put(
                Pattern.compile("(?i)(Failed to execute goal se\\.vandmo:dependency-lock-maven-plugin:.*?:check)"),
                FailureCategory.DEPENDENCY_LOCK_FAILURE);
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
                return entry.getValue();
            }
        }
        return FailureCategory.UNKNOWN;
    }
}

