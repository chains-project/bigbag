package chains.changeimpact.service;

import com.example.japicmp.JapicmpDiffTool;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight index over japicmp class changes.
 */
final class ApiChangeIndex {

    private final Map<String, JapicmpDiffTool.ClassChange> classChanges;

    private ApiChangeIndex(Map<String, JapicmpDiffTool.ClassChange> classChanges) {
        this.classChanges = classChanges;
    }

    static ApiChangeIndex fromReport(JapicmpDiffTool.ComparisonReport report) {
        Map<String, JapicmpDiffTool.ClassChange> classes = new HashMap<>();
        if (report != null && report.changes != null) {
            for (JapicmpDiffTool.ClassChange classChange : report.changes) {
                classes.put(classChange.fullyQualifiedName, classChange);
            }
        }
        return new ApiChangeIndex(Collections.unmodifiableMap(classes));
    }

    JapicmpDiffTool.ClassChange classChange(String fullyQualifiedName) {
        return classChanges.get(fullyQualifiedName);
    }
}

