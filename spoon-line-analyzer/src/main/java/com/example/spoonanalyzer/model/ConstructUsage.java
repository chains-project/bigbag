package com.example.spoonanalyzer.model;

import spoon.reflect.cu.SourcePosition;

import java.util.Objects;

public final class ConstructUsage {

    private final ConstructType constructType;
    private final String signature;
    private final DependencyInfo dependencyInfo;
    private final SourcePosition sourcePosition;

    public ConstructUsage(ConstructType constructType,
                          String signature,
                          DependencyInfo dependencyInfo,
                          SourcePosition sourcePosition) {
        this.constructType = Objects.requireNonNull(constructType, "constructType");
        this.signature = Objects.requireNonNull(signature, "signature");
        this.dependencyInfo = Objects.requireNonNull(dependencyInfo, "dependencyInfo");
        this.sourcePosition = sourcePosition;
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
                && Objects.equals(sourcePositionKey(sourcePosition), sourcePositionKey(that.sourcePosition));
    }

    @Override
    public int hashCode() {
        return Objects.hash(constructType, signature, dependencyInfo, sourcePositionKey(sourcePosition));
    }

    private static String sourcePositionKey(SourcePosition position) {
        if (position == null || !position.isValidPosition()) {
            return "unknown";
        }
        return position.getFile().getAbsolutePath() + ":" + position.getLine();
    }
}

