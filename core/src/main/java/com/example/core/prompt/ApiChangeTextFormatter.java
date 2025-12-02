package com.example.core.prompt;

import chains.changeimpact.model.ApiChangeMatch;
import chains.changeimpact.model.CompatibilityChangeSummary;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility to render {@link ApiChangeMatch} instances into a compact,
 * single-line textual representation suitable for prompts, e.g.:
 * <pre>
 * - CLASS net.sf.jasperreports.charts.base.JRBaseBar3DPlot
 *   [status=UNCHANGED, binaryCompatible=false, sourceCompatible=false]
 *   changes=METHOD_REMOVED_IN_SUPERCLASS(MAJOR)
 * </pre>
 */
public final class ApiChangeTextFormatter {

    private ApiChangeTextFormatter() {
        // utility
    }

    /**
     * Format a single {@link ApiChangeMatch} as a one-line string:
     * <pre>
     * - CLASS fully.qualified.Signature [status=..., binaryCompatible=..., sourceCompatible=...] changes=TYPE(IMPACT),...
     * </pre>
     */
    public static String format(ApiChangeMatch match) {
        if (match == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("- ")
          .append(match.elementType() != null ? match.elementType() : "UNKNOWN")
          .append(' ')
          .append(match.qualifiedSignature() != null ? match.qualifiedSignature() : match.name());

        sb.append(" [status=")
          .append(match.changeStatus() != null ? match.changeStatus() : "UNKNOWN")
          .append(", binaryCompatible=")
          .append(match.binaryCompatible())
          .append(", sourceCompatible=")
          .append(match.sourceCompatible())
          .append(']');

        List<CompatibilityChangeSummary> changes = match.compatibilityChanges();
        if (changes != null && !changes.isEmpty()) {
            String joined = changes.stream()
                    .map(cc -> cc.type() + "(" + cc.semanticVersionImpact() + ")")
                    .collect(Collectors.joining(", "));
            if (!joined.isBlank()) {
                sb.append(" changes=").append(joined);
            }
        }

        return sb.toString();
    }
}


