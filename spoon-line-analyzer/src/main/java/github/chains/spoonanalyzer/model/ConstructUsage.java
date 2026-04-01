package github.chains.spoonanalyzer.model;

import spoon.reflect.cu.SourcePosition;

import java.util.Objects;

public final class ConstructUsage {

    private final ConstructType constructType;
    private final String signature;
    private final DependencyInfo dependencyInfo;
    private final SourcePosition sourcePosition;
    private final String codeLine;
    private final String fullyQualifiedName;
    private final String memberQualifiedName;

    public ConstructUsage(ConstructType constructType,
                          String signature,
                          DependencyInfo dependencyInfo,
                          SourcePosition sourcePosition,
                          String codeLine) {
        this(constructType, signature, dependencyInfo, sourcePosition, codeLine, null, null);
    }

    public ConstructUsage(ConstructType constructType,
                          String signature,
                          DependencyInfo dependencyInfo,
                          SourcePosition sourcePosition,
                          String codeLine,
                          String fullyQualifiedName) {
        this(constructType, signature, dependencyInfo, sourcePosition, codeLine, fullyQualifiedName, null);
    }

    public ConstructUsage(ConstructType constructType,
                          String signature,
                          DependencyInfo dependencyInfo,
                          SourcePosition sourcePosition,
                          String codeLine,
                          String fullyQualifiedName,
                          String memberQualifiedName) {
        this.constructType = Objects.requireNonNull(constructType, "constructType");
        this.signature = Objects.requireNonNull(signature, "signature");
        this.dependencyInfo = Objects.requireNonNull(dependencyInfo, "dependencyInfo");
        this.sourcePosition = sourcePosition;
        this.codeLine = codeLine;
        this.fullyQualifiedName = fullyQualifiedName;
        this.memberQualifiedName = memberQualifiedName;
    }

    public ConstructType getConstructType() {
        return constructType;
    }

    public String getSignature() {
        return signature;
    }

    public DependencyInfo getDependencyInfo() {
        return dependencyInfo;
    }

    public SourcePosition getSourcePosition() {
        return sourcePosition;
    }

    public String getCodeLine() {
        return codeLine;
    }

    public String getFullyQualifiedName() {
        return fullyQualifiedName;
    }

    public String getMemberQualifiedName() {
        return memberQualifiedName;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(constructType.name())
                .append(" -> ")
                .append(signature)
                .append(" | ")
                .append(dependencyInfo.shortCoordinates());
        if (sourcePosition != null && sourcePosition.isValidPosition()) {
            builder.append(" @ ")
                    .append(sourcePosition.getFile().getName())
                    .append(":")
                    .append(sourcePosition.getLine());
        }
        if (codeLine != null && !codeLine.isBlank()) {
            builder.append(" | line=\"")
                    .append(codeLine.trim())
                    .append("\"");
        }
        return builder.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ConstructUsage)) {
            return false;
        }
        ConstructUsage that = (ConstructUsage) o;
        return constructType == that.constructType
                && Objects.equals(signature, that.signature)
                && Objects.equals(dependencyInfo, that.dependencyInfo)
                && Objects.equals(sourcePositionKey(sourcePosition), sourcePositionKey(that.sourcePosition))
                && Objects.equals(codeLineNormalized(codeLine), codeLineNormalized(that.codeLine))
                && Objects.equals(fullyQualifiedName, that.fullyQualifiedName)
                && Objects.equals(memberQualifiedName, that.memberQualifiedName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(constructType, signature, dependencyInfo, sourcePositionKey(sourcePosition), codeLineNormalized(codeLine), fullyQualifiedName, memberQualifiedName);
    }

    private static String sourcePositionKey(SourcePosition position) {
        if (position == null || !position.isValidPosition()) {
            return "unknown";
        }
        return position.getFile().getAbsolutePath() + ":" + position.getLine();
    }

    private static String codeLineNormalized(String codeLine) {
        return codeLine == null ? "" : codeLine.strip();
    }
}

