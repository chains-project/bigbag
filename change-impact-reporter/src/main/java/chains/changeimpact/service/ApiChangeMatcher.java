package chains.changeimpact.service;

import chains.changeimpact.model.ApiChangeMatch;
import chains.changeimpact.model.ConstructImpact;
import chains.changeimpact.model.DependencySummary;
import com.example.japicmp.model.ClassChange;
import com.example.japicmp.model.MemberChange;
import com.example.spoonanalyzer.model.ConstructType;
import com.example.spoonanalyzer.model.ConstructUsage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

final class ApiChangeMatcher {

    private final ApiChangeIndex index;

    ApiChangeMatcher(ApiChangeIndex index) {
        this.index = index;
    }

    List<ConstructImpact> match(List<ConstructUsage> usages) {
        List<ConstructImpact> results = new ArrayList<>(usages.size());
        for (ConstructUsage usage : usages) {
            results.add(mapConstruct(usage));
        }
        return results;
    }

    private ConstructImpact mapConstruct(ConstructUsage usage) {
        ParsedSignature parsed = ParsedSignature.from(usage);
        List<ApiChangeMatch> matches = new ArrayList<>();

        ClassChange classChange = parsed != null
                ? index.classChange(parsed.declaringType())
                : null;

        boolean appendClassSummary = true;
        if (parsed != null && classChange != null) {
            ConstructType type = usage.getConstructType();
            switch (type) {
                case FIELD_ACCESS -> matches.addAll(matchField(classChange, parsed));
                case METHOD_INVOCATION, CONSTRUCTOR_CALL -> matches.addAll(matchExecutable(classChange, parsed, type));
                default -> {
                    matches.addAll(matchTypeLevel(classChange));
                    appendClassSummary = false;
                }
            }

            if (appendClassSummary) {
                matches.addAll(classLevelIfRelevant(classChange));
            }
        } else if (parsed != null) {
            matches.addAll(matchTypeLevel(classChange));
        }

        return new ConstructImpact(
                usage.getConstructType().name(),
                usage.getSignature(),
                DependencySummary.from(usage.getDependencyInfo()),
                matches
        );
    }

    private List<ApiChangeMatch> classLevelIfRelevant(ClassChange classChange) {
        if (classChange == null) {
            return List.of();
        }
        boolean hasRelevantChange = !"UNCHANGED".equalsIgnoreCase(classChange.changeStatus())
                || classChange.changedMemberCount() > 0
                || (classChange.compatibilityChanges() != null && !classChange.compatibilityChanges().isEmpty());
        if (!hasRelevantChange) {
            return List.of();
        }
        return List.of(ApiChangeMatch.fromClassChange(classChange));
    }

    private List<ApiChangeMatch> matchTypeLevel(ClassChange classChange) {
        if (classChange == null) {
            return List.of();
        }
        return List.of(ApiChangeMatch.fromClassChange(classChange));
    }

    private List<ApiChangeMatch> matchField(ClassChange classChange, ParsedSignature parsed) {
        if (classChange.detail() == null || parsed.memberName() == null) {
            return List.of();
        }
        return classChange.detail().fields().stream()
                .filter(field -> Objects.equals(field.name(), parsed.memberName()))
                .map(field -> ApiChangeMatch.fromMemberChange(field, "FIELD_EXACT", classChange.fullyQualifiedName()))
                .collect(Collectors.toList());
    }

    private List<ApiChangeMatch> matchExecutable(ClassChange classChange,
                                                 ParsedSignature parsed,
                                                 ConstructType constructType) {
        List<ApiChangeMatch> matches = new ArrayList<>();

        if (parsed.memberName() == null) {
            return matches;
        }

        if (classChange.detail() != null) {
            List<MemberChange> candidates = constructType == ConstructType.CONSTRUCTOR_CALL
                    ? classChange.detail().constructors()
                    : classChange.detail().methods();

            List<ApiChangeMatch> exactMatches = candidates.stream()
                    .filter(member -> Objects.equals(member.name(), parsed.memberName()))
                    .filter(member -> parameterTypesEqual(parsed.parameterTypes(), member.parameterTypes()))
                    .map(member -> ApiChangeMatch.fromMemberChange(member, "EXACT_SIGNATURE", classChange.fullyQualifiedName()))
                    .collect(Collectors.toList());

            matches.addAll(exactMatches);

            List<ApiChangeMatch> nameOnlyMatches = candidates.stream()
                    .filter(member -> Objects.equals(member.name(), parsed.memberName()))
                    .filter(member -> !parameterTypesEqual(parsed.parameterTypes(), member.parameterTypes()))
                    .map(member -> ApiChangeMatch.fromMemberChange(member, "NAME_ONLY", classChange.fullyQualifiedName()))
                    .collect(Collectors.toList());

            for (ApiChangeMatch match : nameOnlyMatches) {
                if (!matches.contains(match)) {
                    matches.add(match);
                }
            }
        }

        matches.addAll(matchAcrossClasses(parsed, constructType, classChange, matches));

        return matches;
    }

    private boolean parameterTypesEqual(List<String> left, List<String> right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (!Objects.equals(left.get(i), right.get(i))) {
                return false;
            }
        }
        return true;
    }

    private List<ApiChangeMatch> matchAcrossClasses(ParsedSignature parsed,
                                                    ConstructType constructType,
                                                    ClassChange currentClass,
                                                    List<ApiChangeMatch> existingMatches) {
        if (parsed == null || parsed.memberName() == null) {
            return List.of();
        }

        List<ApiChangeMatch> crossClassMatches = new ArrayList<>();

        for (ApiChangeIndex.MemberEntry entry : index.membersWithName(parsed.memberName())) {
            MemberChange candidate = entry.member();
            if (!memberTypeMatches(constructType, candidate.memberType())) {
                continue;
            }
            if (isSameDeclaringType(currentClass, entry.declaringType())) {
                continue;
            }
            if ("UNCHANGED".equalsIgnoreCase(candidate.changeStatus())) {
                continue;
            }

            String matchType = parameterTypesEqual(parsed.parameterTypes(), candidate.parameterTypes())
                    ? "NAME_AND_PARAMS_OTHER_CLASS"
                    : "NAME_ONLY_OTHER_CLASS";

            ApiChangeMatch match = ApiChangeMatch.fromMemberChange(candidate, matchType, entry.declaringType());
            if (!existingMatches.contains(match) && !crossClassMatches.contains(match)) {
                crossClassMatches.add(match);
            }
        }

        return crossClassMatches;
    }

    private boolean memberTypeMatches(ConstructType constructType, String memberType) {
        return switch (constructType) {
            case METHOD_INVOCATION -> "METHOD".equals(memberType);
            case CONSTRUCTOR_CALL -> "CONSTRUCTOR".equals(memberType);
            default -> false;
        };
    }

    private boolean isSameDeclaringType(ClassChange currentClass, String otherDeclaringType) {
        if (currentClass == null) {
            return false;
        }
        return Objects.equals(currentClass.fullyQualifiedName(), otherDeclaringType);
    }

    private record ParsedSignature(String declaringType,
                                   String memberName,
                                   List<String> parameterTypes) {

        static ParsedSignature from(ConstructUsage usage) {
            String signature = usage.getSignature();
            return switch (usage.getConstructType()) {
                case METHOD_INVOCATION, CONSTRUCTOR_CALL -> parseExecutable(signature, usage.getConstructType());
                case FIELD_ACCESS -> parseField(signature);
                case TYPE_REFERENCE, ANNOTATION_USAGE, IMPORT -> new ParsedSignature(signature, null, List.of());
                default -> null;
            };
        }

        private static ParsedSignature parseExecutable(String signature, ConstructType type) {
            int hashIndex = signature.indexOf('#');
            if (hashIndex < 0) {
                return null;
            }
            String declaringType = signature.substring(0, hashIndex);
            String rest = signature.substring(hashIndex + 1);
            int parenIndex = rest.indexOf('(');
            String name = parenIndex >= 0 ? rest.substring(0, parenIndex) : rest;
            String paramsSection = parenIndex >= 0
                    ? rest.substring(parenIndex + 1, Math.max(rest.lastIndexOf(')'), parenIndex))
                    : "";
            List<String> parameters = paramsSection.isBlank()
                    ? List.of()
                    : Arrays.stream(paramsSection.split(","))
                    .map(String::trim)
                    .filter(part -> !part.isEmpty())
                    .collect(Collectors.toUnmodifiableList());

            if (type == ConstructType.CONSTRUCTOR_CALL) {
                name = simpleName(declaringType);
            }
            return new ParsedSignature(declaringType, name, parameters);
        }

        private static ParsedSignature parseField(String signature) {
            int separator = signature.indexOf("::");
            if (separator < 0) {
                return null;
            }
            String declaringType = signature.substring(0, separator);
            String fieldName = signature.substring(separator + 2);
            return new ParsedSignature(declaringType, fieldName, List.of());
        }

        private static String simpleName(String typeName) {
            int lastDot = typeName.lastIndexOf('.');
            if (lastDot < 0 || lastDot == typeName.length() - 1) {
                return typeName;
            }
            return typeName.substring(lastDot + 1);
        }
    }
}

