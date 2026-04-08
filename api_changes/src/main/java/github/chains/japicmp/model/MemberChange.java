package github.chains.japicmp.model;

import java.util.List;

public record MemberChange(String memberType,
                           String name,
                           String changeStatus,
                           boolean binaryCompatible,
                           boolean sourceCompatible,
                           List<CompatibilityChangeInfo> compatibilityChanges,
                           ValueChange<String> signature,
                           ValueChange<String> value,
                           List<String> oldModifiers,
                           List<String> newModifiers,
                           List<AnnotationDetail> annotations,
                           List<String> parameterTypes) {

    public MemberChange {
        compatibilityChanges = List.copyOf(compatibilityChanges);
        oldModifiers = List.copyOf(oldModifiers);
        newModifiers = List.copyOf(newModifiers);
        annotations = List.copyOf(annotations);
        parameterTypes = List.copyOf(parameterTypes);
    }
}

