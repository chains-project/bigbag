package chains.changeimpact.service;

import com.example.japicmp.model.ClassChange;
import com.example.japicmp.model.ClassDetail;
import com.example.japicmp.model.ComparisonReport;
import com.example.japicmp.model.MemberChange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight index over japicmp class changes.
 */
final class ApiChangeIndex {

    private final Map<String, ClassChange> classChanges;
    private final Map<String, List<MemberEntry>> membersByName;
    private final Map<String, List<MemberEntry>> membersByQualifiedName;

    private ApiChangeIndex(Map<String, ClassChange> classChanges,
                           Map<String, List<MemberEntry>> membersByName,
                           Map<String, List<MemberEntry>> membersByQualifiedName) {
        this.classChanges = classChanges;
        this.membersByName = membersByName;
        this.membersByQualifiedName = membersByQualifiedName;
    }

    static ApiChangeIndex fromReport(ComparisonReport report) {
        Map<String, ClassChange> classes = new HashMap<>();
        Map<String, List<MemberEntry>> members = new HashMap<>();
        Map<String, List<MemberEntry>> membersByFqn = new HashMap<>();
        if (report != null && report.changes() != null) {
            for (ClassChange classChange : report.changes()) {
                classes.put(classChange.fullyQualifiedName(), classChange);
                ClassDetail detail = classChange.detail();
                if (detail != null) {
                    indexMembers(members, membersByFqn, classChange.fullyQualifiedName(), detail.constructors());
                    indexMembers(members, membersByFqn, classChange.fullyQualifiedName(), detail.methods());
                    indexMembers(members, membersByFqn, classChange.fullyQualifiedName(), detail.fields());
                }
            }
        }
        members.replaceAll((name, entries) -> List.copyOf(entries));
        membersByFqn.replaceAll((name, entries) -> List.copyOf(entries));
        return new ApiChangeIndex(
                Collections.unmodifiableMap(classes),
                Collections.unmodifiableMap(members),
                Collections.unmodifiableMap(membersByFqn)
        );
    }

    ClassChange classChange(String fullyQualifiedName) {
        return classChanges.get(fullyQualifiedName);
    }

    List<MemberEntry> membersWithName(String name) {
        return membersByName.getOrDefault(name, List.of());
    }

    List<MemberEntry> membersWithQualifiedName(String qualifiedName) {
        return membersByQualifiedName.getOrDefault(qualifiedName, List.of());
    }

    private static void indexMembers(Map<String, List<MemberEntry>> members,
                                     Map<String, List<MemberEntry>> membersByQualifiedName,
                                     String declaringType,
                                     List<MemberChange> changes) {
        if (changes == null) {
            return;
        }
        for (MemberChange change : changes) {
            MemberEntry entry = new MemberEntry(declaringType, change);
            members.computeIfAbsent(change.name(), key -> new ArrayList<>())
                    .add(entry);
            String key = declaringType + "." + change.name();
            membersByQualifiedName.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(entry);
        }
    }

    static final class MemberEntry {
        private final String declaringType;
        private final MemberChange member;

        MemberEntry(String declaringType, MemberChange member) {
            this.declaringType = declaringType;
            this.member = member;
        }

        String declaringType() {
            return declaringType;
        }

        MemberChange member() {
            return member;
        }
    }
}

