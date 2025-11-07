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
                             String declaringType,
                             String qualifiedSignature,
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
                classChange.fullyQualifiedName,
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

    public static ApiChangeMatch fromMemberChange(JapicmpDiffTool.MemberChange memberChange,
                                                  String matchType,
                                                  String declaringType) {
        Objects.requireNonNull(matchType, "matchType");
        List<CompatibilityChangeSummary> compatibilitySummaries = memberChange.compatibilityChanges == null
                ? List.of()
                : memberChange.compatibilityChanges.stream()
                .map(CompatibilityChangeSummary::from)
                .collect(Collectors.toUnmodifiableList());

        List<String> params = memberChange.parameterTypes == null
                ? List.of()
                : List.copyOf(memberChange.parameterTypes);

        String qualifiedSignature = formatQualifiedSignature(declaringType, memberChange.name, memberChange.memberType, params);

        return new ApiChangeMatch(
                memberChange.memberType,
                matchType,
                memberChange.name,
                declaringType,
                qualifiedSignature,
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

    private static String formatQualifiedSignature(String declaringType,
                                                   String memberName,
                                                   String memberType,
                                                   List<String> parameterTypes) {
        if (declaringType == null || declaringType.isBlank()) {
            return memberName;
        }
        List<String> params = parameterTypes == null ? List.of() : parameterTypes;
        return switch (memberType) {
            case "METHOD" -> declaringType + "." + memberName + formatParameters(params);
            case "CONSTRUCTOR" -> declaringType + "." + memberName + formatParameters(params);
            case "FIELD" -> declaringType + "." + memberName;
            default -> declaringType + "." + memberName;
        };
    }

    private static String formatParameters(List<String> parameterTypes) {
        if (parameterTypes == null || parameterTypes.isEmpty()) {
            return "()";
        }
        return parameterTypes.stream()
                .map(param -> param == null ? "null" : param)
                .collect(Collectors.joining(", ", "(", ")"));
    }
}

