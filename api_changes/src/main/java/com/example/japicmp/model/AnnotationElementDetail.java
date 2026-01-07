package com.example.japicmp.model;

import java.util.List;

public record AnnotationElementDetail(String name,
                                      String changeStatus,
                                      List<String> oldValues,
                                      List<String> newValues,
                                      List<CompatibilityChangeInfo> compatibilityChanges) {

    public AnnotationElementDetail {
        oldValues = List.copyOf(oldValues);
        newValues = List.copyOf(newValues);
        compatibilityChanges = List.copyOf(compatibilityChanges);
    }
}

