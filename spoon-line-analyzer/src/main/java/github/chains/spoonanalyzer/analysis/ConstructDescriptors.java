package github.chains.spoonanalyzer.analysis;

import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;

final class ConstructDescriptors {

    private ConstructDescriptors() {
    }

    static String describeExecutable(CtExecutableReference<?> executableReference) {
        if (executableReference == null) {
            return "<unknown executable>";
        }
        String declaringType = executableReference.getDeclaringType() != null
                ? executableReference.getDeclaringType().getQualifiedName()
                : "<unknown type>";
        StringBuilder builder = new StringBuilder(declaringType)
                .append("#")
                .append(executableReference.getSimpleName())
                .append("(");
        if (executableReference.getParameters() != null) {
            builder.append(executableReference.getParameters().stream()
                    .map(CtTypeReference::getQualifiedName)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse(""));
        }
        builder.append(")");
        return builder.toString();
    }

    static String describeField(CtFieldReference<?> fieldReference) {
        if (fieldReference == null) {
            return "<unknown field>";
        }
        String declaringType = fieldReference.getDeclaringType() != null
                ? fieldReference.getDeclaringType().getQualifiedName()
                : "<unknown type>";
        return declaringType + "::" + fieldReference.getSimpleName();
    }

    static String describeType(CtTypeReference<?> typeReference) {
        if (typeReference == null) {
            return "<unknown type>";
        }
        return typeReference.getQualifiedName();
    }
}

