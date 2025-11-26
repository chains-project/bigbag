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
        // Use FQN directly if available, otherwise fall back to parsing signature
        String fqn = usage.getFullyQualifiedName();
        ClassChange classChange = fqn != null && !fqn.isBlank()
                ? index.classChange(fqn)
                : null;

        // If FQN didn't match, try parsing signature as fallback
        ParsedSignature parsed = ParsedSignature.from(usage);
        if (classChange == null && parsed != null && parsed.declaringType() != null) {
            classChange = index.classChange(parsed.declaringType());
        }

        List<ApiChangeMatch> matches = new ArrayList<>();
        boolean appendClassSummary = true;

        if (classChange != null) {
            ConstructType type = usage.getConstructType();
            
            switch (type) {
                case FIELD_ACCESS -> matches.addAll(matchField(classChange, parsed));
                case METHOD_INVOCATION, CONSTRUCTOR_CALL -> matches.addAll(matchExecutable(classChange, parsed, type));
                case TYPE_REFERENCE -> {
                    // TYPE_REFERENCE might actually be a field access that Spoon couldn't resolve
                    // Try to extract field name and search for it
                    String possibleFieldName = extractPossibleFieldName(usage.getSignature());
                    if (possibleFieldName != null) {
                        matches.addAll(matchFieldByNameOnly(possibleFieldName));
                    }
                    // Also include type-level changes
                    matches.addAll(matchTypeLevel(classChange));
                    appendClassSummary = false;
                }
                default -> {
                    matches.addAll(matchTypeLevel(classChange));
                    appendClassSummary = false;
                }
            }

            if (appendClassSummary) {
                matches.addAll(classLevelIfRelevant(classChange));
            }
        } else {
            // No classChange found - try various fallback strategies
            ConstructType type = usage.getConstructType();
            String signature = usage.getSignature();
            
            if (fqn != null && !fqn.isBlank()) {
                // Try direct FQN match (might be a type reference)
                classChange = index.classChange(fqn);
                if (classChange != null) {
                    matches.addAll(matchTypeLevel(classChange));
                }
            }
            
            // For TYPE_REFERENCE, always try to extract field name from signature
            // This handles cases like "quickfix.mina.ssl.PEER_ADDRESS" where Spoon couldn't resolve it
            // This is critical because Spoon often detects unresolved static fields as TYPE_REFERENCE
            // when the model doesn't have full context (e.g., model built per-file instead of full project)
            if (type == ConstructType.TYPE_REFERENCE && signature != null 
                    && !signature.contains("#") && !signature.contains("::")) {
                String possibleFieldName = extractPossibleFieldName(signature);
                if (possibleFieldName != null) {
                    matches.addAll(matchFieldByNameOnly(possibleFieldName));
                }
            }
            
            // If we have a parsed signature with member name, search by name
            if (parsed != null && parsed.memberName() != null) {
                matches.addAll(matchByNameOnly(parsed, type));
            }
        }

        Integer lineNumber = null;
        if (usage.getSourcePosition() != null && usage.getSourcePosition().isValidPosition()) {
            lineNumber = usage.getSourcePosition().getLine();
        }

        return new ConstructImpact(
                usage.getConstructType().name(),
                usage.getSignature(),
                DependencySummary.from(usage.getDependencyInfo()),
                lineNumber,
                usage.getCodeLine(),
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

    private List<ApiChangeMatch> matchByNameOnly(ParsedSignature parsed, ConstructType constructType) {
        if (parsed == null || parsed.memberName() == null) {
            return List.of();
        }

        List<ApiChangeMatch> matches = new ArrayList<>();

        for (ApiChangeIndex.MemberEntry entry : index.membersWithName(parsed.memberName())) {
            MemberChange candidate = entry.member();
            if (!memberTypeMatches(constructType, candidate.memberType())) {
                continue;
            }
            // Only include removed or modified methods (not unchanged)
            if ("UNCHANGED".equalsIgnoreCase(candidate.changeStatus())) {
                continue;
            }

            String matchType = parameterTypesEqual(parsed.parameterTypes(), candidate.parameterTypes())
                    ? "EXACT_SIGNATURE_CROSS_CLASS"
                    : "NAME_ONLY_CROSS_CLASS";

            ApiChangeMatch match = ApiChangeMatch.fromMemberChange(candidate, matchType, entry.declaringType());
            if (!matches.contains(match)) {
                matches.add(match);
            }
        }

        return matches;
    }

    private List<ApiChangeMatch> matchFieldByNameOnly(String fieldName) {
        List<ApiChangeMatch> matches = new ArrayList<>();

        for (ApiChangeIndex.MemberEntry entry : index.membersWithName(fieldName)) {
            MemberChange candidate = entry.member();
            // Only match fields
            if (!"FIELD".equals(candidate.memberType())) {
                continue;
            }
            // Only include removed or modified fields (not unchanged)
            if ("UNCHANGED".equalsIgnoreCase(candidate.changeStatus())) {
                continue;
            }

            ApiChangeMatch match = ApiChangeMatch.fromMemberChange(candidate, "FIELD_EXACT_CROSS_CLASS", entry.declaringType());
            if (!matches.contains(match)) {
                matches.add(match);
            }
        }

        return matches;
    }

    private String extractPossibleFieldName(String signature) {
        if (signature == null || signature.isBlank()) {
            return null;
        }
        // Try to extract field name from patterns like:
        // - "quickfix.mina.ssl.PEER_ADDRESS" -> "PEER_ADDRESS"
        // - "SomeClass.FIELD_NAME" -> "FIELD_NAME"
        int lastDot = signature.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < signature.length() - 1) {
            String possibleName = signature.substring(lastDot + 1);
            // Check if it looks like a field name (uppercase with underscores is common for constants)
            if (possibleName.matches("[A-Z_][A-Z0-9_]*") || possibleName.matches("[a-z][a-zA-Z0-9]*")) {
                return possibleName;
            }
        }
        // If no dot, the whole signature might be the field name
        if (signature.matches("[A-Z_][A-Z0-9_]*") || signature.matches("[a-z][a-zA-Z0-9]*")) {
            return signature;
        }
        return null;
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

