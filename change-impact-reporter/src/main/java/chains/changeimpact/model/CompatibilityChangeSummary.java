package chains.changeimpact.model;

import com.example.japicmp.JapicmpDiffTool;

/**
 * Lightweight copy of japicmp compatibility change information for JSON output.
 */
public record CompatibilityChangeSummary(String type,
                                         boolean binaryCompatible,
                                         boolean sourceCompatible,
                                         String semanticVersionImpact) {

    public static CompatibilityChangeSummary from(JapicmpDiffTool.CompatibilityChangeInfo info) {
        return new CompatibilityChangeSummary(
                info.type,
                info.binaryCompatible,
                info.sourceCompatible,
                info.semanticVersionImpact
        );
    }
}

