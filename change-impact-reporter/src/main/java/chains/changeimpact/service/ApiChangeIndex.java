package chains.changeimpact.service;

import com.example.japicmp.JapicmpDiffTool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight index over japicmp class changes.
 */
final class ApiChangeIndex {

    private final Map<String, JapicmpDiffTool.ClassChange> classChanges;
    private final Map<String, List<MemberEntry>> membersByName;

    private ApiChangeIndex(Map<String, JapicmpDiffTool.ClassChange> classChanges,
                           Map<String, List<MemberEntry>> membersByName) {
        this.classChanges = classChanges;
        this.membersByName = membersByName;
    }

    static ApiChangeIndex fromReport(JapicmpDiffTool.ComparisonReport report) {
        Map<String, JapicmpDiffTool.ClassChange> classes = new HashMap<>();
        Map<String, List<MemberEntry>> members = new HashMap<>();
        if (report != null && report.changes != null) {
            for (JapicmpDiffTool.ClassChange classChange : report.changes) {
                classes.put(classChange.fullyQualifiedName, classChange);
                if (classChange.detail != null) {
                    indexMembers(members, classChange.fullyQualifiedName, classChange.detail.constructors);
                    indexMembers(members, classChange.fullyQualifiedName, classChange.detail.methods);
                    indexMembers(members, classChange.fullyQualifiedName, classChange.detail.fields);
                }
            }
        }
        members.replaceAll((name, entries) -> List.copyOf(entries));
        return new ApiChangeIndex(
                Collections.unmodifiableMap(classes),
                Collections.unmodifiableMap(members)
        );
    }

    JapicmpDiffTool.ClassChange classChange(String fullyQualifiedName) {
        return classChanges.get(fullyQualifiedName);
    }

    List<MemberEntry> membersWithName(String name) {
        return membersByName.getOrDefault(name, List.of());
    }

    private static void indexMembers(Map<String, List<MemberEntry>> members,
                                     String declaringType,
                                     List<JapicmpDiffTool.MemberChange> changes) {
        if (changes == null) {
            return;
        }
        for (JapicmpDiffTool.MemberChange change : changes) {
            members.computeIfAbsent(change.name, key -> new ArrayList<>())
                    .add(new MemberEntry(declaringType, change));
        }
    }

    static final class MemberEntry {
        private final String declaringType;
        private final JapicmpDiffTool.MemberChange member;

        MemberEntry(String declaringType, JapicmpDiffTool.MemberChange member) {
            this.declaringType = declaringType;
            this.member = member;
        }

        String declaringType() {
            return declaringType;
        }

        JapicmpDiffTool.MemberChange member() {
            return member;
        }
    }
}

