package chains.changeimpact.model;

import com.example.japicmp.JapicmpDiffTool;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents a japicmp change that relates to a Spoon construct.
 */
public record ApiChangeMatch(String elementType,
                             String matchType,
                             String name,
                             String changeStatus,
                             boolean binaryCompatible,
                             boolean sourceCompatible,
                             List<CompatibilityChangeSummary> compatibilityChanges,
                             ValueChangeSummary signature,
                             ValueChangeSummary value,
                             List<String> parameterTypes,
                             Integer changedMemberCount) {

    public ApiChangeMatch {
        compatibilityChanges = compatibilityChanges == null ? List.of() : List.copyOf(compatibilityChanges);
        parameterTypes = parameterTypes == null ? null : List.copyOf(parameterTypes);
    }

    public static ApiChangeMatch fromClassChange(JapicmpDiffTool.ClassChange classChange) {
        List<CompatibilityChangeSummary> compatibilitySummaries = classChange.compatibilityChanges == null
                ? List.of()
                : classChange.compatibilityChanges.stream()
                .map(CompatibilityChangeSummary::from)
                .collect(Collectors.toUnmodifiableList());

        return new ApiChangeMatch(
                classChange.elementType,
                "CLASS",
                classChange.fullyQualifiedName,
                classChange.changeStatus,
                classChange.binaryCompatible,
                classChange.sourceCompatible,
                compatibilitySummaries,
                null,
                null,
                null,
                classChange.changedMemberCount
        );
    }

    public static ApiChangeMatch fromMemberChange(JapicmpDiffTool.MemberChange memberChange, String matchType) {
        Objects.requireNonNull(matchType, "matchType");
        List<CompatibilityChangeSummary> compatibilitySummaries = memberChange.compatibilityChanges == null
                ? List.of()
                : memberChange.compatibilityChanges.stream()
                .map(CompatibilityChangeSummary::from)
                .collect(Collectors.toUnmodifiableList());

        List<String> params = memberChange.parameterTypes == null
                ? List.of()
                : List.copyOf(memberChange.parameterTypes);

        return new ApiChangeMatch(
                memberChange.memberType,
                matchType,
                memberChange.name,
                memberChange.changeStatus,
                memberChange.binaryCompatible,
                memberChange.sourceCompatible,
                compatibilitySummaries,
                ValueChangeSummary.from(memberChange.signature),
                ValueChangeSummary.from(memberChange.value),
                params,
                null
        );
    }
}

