package github.chains.japicmp.model;

import java.util.List;

public record InterfaceChange(String name,
                              String changeStatus,
                              boolean binaryCompatible,
                              boolean sourceCompatible,
                              List<CompatibilityChangeInfo> compatibilityChanges) {

    public InterfaceChange {
        compatibilityChanges = List.copyOf(compatibilityChanges);
    }
}

