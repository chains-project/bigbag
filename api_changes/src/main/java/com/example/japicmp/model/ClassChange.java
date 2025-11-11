package com.example.japicmp.model;

import java.util.List;

public record ClassChange(String elementType,
                          String fullyQualifiedName,
                          String simpleName,
                          String packageName,
                          String changeStatus,
                          boolean binaryCompatible,
                          boolean sourceCompatible,
                          List<CompatibilityChangeInfo> compatibilityChanges,
                          int changedMemberCount,
                          ClassDetail detail) {

    public ClassChange {
        compatibilityChanges = List.copyOf(compatibilityChanges);
    }
}

