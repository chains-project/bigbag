package com.example.japicmp.model;

import java.util.List;

public record AnnotationDetail(String name,
                               String changeStatus,
                               boolean binaryCompatible,
                               boolean sourceCompatible,
                               List<CompatibilityChangeInfo> compatibilityChanges,
                               List<AnnotationElementDetail> elements) {

    public AnnotationDetail {
        compatibilityChanges = List.copyOf(compatibilityChanges);
        elements = List.copyOf(elements);
    }
}

