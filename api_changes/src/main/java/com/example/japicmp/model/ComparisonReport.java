package com.example.japicmp.model;

import java.util.List;

public record ComparisonReport(String oldJar,
                               String newJar,
                               String generatedAt,
                               Summary summary,
                               List<ClassChange> changes) {

    public ComparisonReport {
        changes = List.copyOf(changes);
    }
}

