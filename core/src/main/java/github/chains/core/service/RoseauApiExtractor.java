package github.chains.core.service;

import github.chains.core.model.BreakingUpdateRecord;
import github.chains.core.model.UpdatedDependency;
import io.github.alien.roseau.Library;
import io.github.alien.roseau.Roseau;
import io.github.alien.roseau.api.model.API;
import io.github.alien.roseau.api.model.ClassDecl;
import io.github.alien.roseau.api.model.FieldDecl;
import io.github.alien.roseau.api.model.LibraryTypes;
import io.github.alien.roseau.api.model.MethodDecl;
import io.github.alien.roseau.api.model.TypeDecl;
import io.github.alien.roseau.diff.RoseauReport;
import io.github.alien.roseau.diff.changes.BreakingChange;
import io.github.alien.roseau.diff.formatter.BreakingChangesFormatterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generates Roseau API representations for both versions of a dependency
 * and copies the resulting files into the commit report directory.
 *
 * <p>Output files written to {@code commitReportDir}:
 * <ul>
 *   <li>{@code roseau-api-v1.json} — full API model of the previous version (JSON)</li>
 *   <li>{@code roseau-api-v2.json} — full API model of the new version (JSON)</li>
 *   <li>{@code roseau-api-v2.md}   — human-readable API spec of the new version (for the agent)</li>
 *   <li>{@code roseau-api-diff.md} — breaking changes in readable diff format (for the prompt)</li>
 *   <li>{@code roseau-breaking-changes.json} — breaking changes in JSON format</li>
 * </ul>
 */
public class RoseauApiExtractor {

    private static final Logger log = LoggerFactory.getLogger(RoseauApiExtractor.class);

    /**
     * Generates all Roseau API files for both dependency versions.
     *
     * <p>JAR resolution order for each version:
     * <ol>
     *   <li>{@code extractedPath/{artifactId}-{version}.jar}</li>
     *   <li>{@code extractedPath/m2/.m2/repository/{group}/{artifact}/{version}/...jar}</li>
     *   <li>{@code ~/.m2/repository/{group}/{artifact}/{version}/...jar}</li>
     * </ol>
     */
    public void extractAndCopy(BreakingUpdateRecord record, Path extractedPath, Path commitReportDir) {
        if (record == null || record.updatedDependency() == null || commitReportDir == null) {
            log.warn("RoseauApiExtractor: missing record or commitReportDir — skipping");
            return;
        }

        UpdatedDependency dep = record.updatedDependency();
        String groupId     = dep.dependencyGroupId();
        String artifactId  = dep.dependencyArtifactId();
        String prevVersion = dep.previousVersion();
        String newVersion  = dep.newVersion();

        if (isBlank(groupId) || isBlank(artifactId) || isBlank(prevVersion) || isBlank(newVersion)) {
            log.warn("RoseauApiExtractor: incomplete dependency coordinates ({}) — skipping", dep.classifier());
            return;
        }

        Path jarV1 = resolveJar(groupId, artifactId, prevVersion, extractedPath);
        Path jarV2 = resolveJar(groupId, artifactId, newVersion, extractedPath);

        if (jarV1 == null || jarV2 == null) {
            log.warn("RoseauApiExtractor: could not locate JAR(s) for {}:{} ({} → {}) — skipping",
                    groupId, artifactId, prevVersion, newVersion);
            return;
        }

        log.info("RoseauApiExtractor: analyzing {}:{} ({} → {})", groupId, artifactId, prevVersion, newVersion);
        log.info("RoseauApiExtractor: v1 JAR = {}", jarV1);
        log.info("RoseauApiExtractor: v2 JAR = {}", jarV2);

        try {
            Files.createDirectories(commitReportDir);
        } catch (IOException e) {
            log.warn("RoseauApiExtractor: could not create commitReportDir {}: {}", commitReportDir, e.getMessage());
            return;
        }

        // Build library types for both versions
        LibraryTypes typesV1 = buildLibraryTypes(jarV1, prevVersion);
        if (typesV1 == null) return;

        LibraryTypes typesV2 = buildLibraryTypes(jarV2, newVersion);
        if (typesV2 == null) return;

        // JSON snapshots (raw, for tooling) — named after the JAR file
        writeApiJson(typesV1, commitReportDir.resolve(artifactId + "-" + prevVersion + "-api.json"), prevVersion);
        writeApiJson(typesV2, commitReportDir.resolve(artifactId + "-" + newVersion + "-api.json"), newVersion);

        // Build resolved APIs and compute diff
        API apiV1 = Roseau.buildAPI(typesV1);
        API apiV2 = Roseau.buildAPI(typesV2);
        RoseauReport report = Roseau.diff(apiV1, apiV2);

        // Breaking changes: JSON (compact, for tooling) + Markdown diff (readable, for prompt)
        writeBreakingChangesJson(report,
                commitReportDir.resolve(artifactId + "-breaking-changes.json"),
                prevVersion, newVersion);
        writeDiffMarkdown(report,
                commitReportDir.resolve(artifactId + "-api-diff.md"),
                groupId, artifactId, prevVersion, newVersion);

        // New API spec as Markdown (readable, for agent to consult)
        writeApiSpecMarkdown(apiV2,
                commitReportDir.resolve(artifactId + "-" + newVersion + "-api.md"),
                groupId, artifactId, newVersion);

    }

    // -------------------------------------------------------------------------
    // JAR resolution
    // -------------------------------------------------------------------------

    private Path resolveJar(String groupId, String artifactId, String version, Path extractedPath) {
        String jarName   = artifactId + "-" + version + ".jar";
        String groupPath = groupId.replace('.', '/');

        if (extractedPath != null) {
            // 1. Directly in extractedPath root (both JARs are copied here by JarExtractionService)
            Path direct = extractedPath.resolve(jarName);
            if (Files.isRegularFile(direct)) {
                log.debug("RoseauApiExtractor: found JAR at extracted root: {}", direct);
                return direct;
            }
            // 2. In the project's extracted m2 cache
            Path fromProjectM2 = extractedPath
                    .resolve("m2").resolve(".m2").resolve("repository")
                    .resolve(groupPath).resolve(artifactId).resolve(version).resolve(jarName);
            if (Files.isRegularFile(fromProjectM2)) {
                log.debug("RoseauApiExtractor: found JAR in project m2: {}", fromProjectM2);
                return fromProjectM2;
            }
        }

        // 3. User local Maven repository (~/.m2/repository)
        Path userM2 = Paths.get(System.getProperty("user.home"), ".m2", "repository")
                .resolve(groupPath).resolve(artifactId).resolve(version).resolve(jarName);
        if (Files.isRegularFile(userM2)) {
            log.debug("RoseauApiExtractor: found JAR in ~/.m2: {}", userM2);
            return userM2;
        }

        log.debug("RoseauApiExtractor: JAR not found for {}:{} v{}", groupId, artifactId, version);
        return null;
    }

    // -------------------------------------------------------------------------
    // Roseau API builders
    // -------------------------------------------------------------------------

    private LibraryTypes buildLibraryTypes(Path jar, String version) {
        try {
            Library library = Library.of(jar);
            LibraryTypes types = Roseau.buildLibraryTypes(library);
            log.info("RoseauApiExtractor: extracted {} types from version {}", types.getAllTypes().size(), version);
            return types;
        } catch (Exception e) {
            log.warn("RoseauApiExtractor: failed to build LibraryTypes for {} ({}): {}",
                    jar.getFileName(), version, e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Writers
    // -------------------------------------------------------------------------

    private void writeApiJson(LibraryTypes types, Path outputFile, String version) {
        try {
            types.writeJson(outputFile);
            log.info("RoseauApiExtractor: wrote API JSON for version {} → {}", version, outputFile);
        } catch (IOException e) {
            log.warn("RoseauApiExtractor: failed to write API JSON for {}: {}", version, e.getMessage());
        }
    }

    private void writeBreakingChangesJson(RoseauReport report, Path outputFile,
                                          String prevVersion, String newVersion) {
        try {
            report.writeReport(BreakingChangesFormatterFactory.JSON, outputFile);
            log.info("RoseauApiExtractor: {} breaking change(s) ({} → {}) → {}",
                    report.getBreakingChanges().size(), prevVersion, newVersion, outputFile);
        } catch (Exception e) {
            log.warn("RoseauApiExtractor: failed to write breaking-changes JSON: {}", e.getMessage());
        }
    }

    /**
     * Generates roseau-api-diff.md — breaking changes in a concise diff format
     * suitable for inclusion in the agent prompt.
     *
     * Labels are specialized per breaking change kind so the agent understands
     * exactly what changed and what action is required.
     */
    private void writeDiffMarkdown(RoseauReport report, Path outputFile,
                                   String groupId, String artifactId,
                                   String prevVersion, String newVersion) {
        try {
            List<BreakingChange> changes = report.getBreakingChanges();
            long binaryCount = changes.stream().filter(c -> c.kind().isBinaryBreaking()).count();
            long sourceCount = changes.stream().filter(c -> c.kind().isSourceBreaking()).count();

            StringBuilder sb = new StringBuilder();
            sb.append("# Breaking Changes: ").append(groupId).append(":").append(artifactId)
              .append(" ").append(prevVersion).append(" → ").append(newVersion).append("\n");
            sb.append("Total: ").append(changes.size())
              .append(" (binary-breaking: ").append(binaryCount)
              .append(", source-breaking: ").append(sourceCount).append(")\n\n");

            Map<String, List<BreakingChange>> byType = changes.stream()
                    .collect(Collectors.groupingBy(c -> c.impactedType().getQualifiedName()));

            byType.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        sb.append("## ").append(entry.getKey()).append("\n\n");
                        entry.getValue().stream()
                                .sorted(Comparator.comparing(c -> c.kind().name()))
                                .forEach(bc -> {
                                    String binary = bc.kind().isBinaryBreaking() ? "binary ✗" : "binary ✓";
                                    String source = bc.kind().isSourceBreaking() ? "source ✗" : "source ✓";
                                    sb.append("### ").append(bc.kind().name())
                                      .append(" [").append(binary).append(" | ").append(source).append("]\n");
                                    appendBreakingChangeLines(sb, bc);
                                    sb.append("\n");
                                });
                    });

            Files.writeString(outputFile, sb.toString(), StandardCharsets.UTF_8);
            log.info("RoseauApiExtractor: wrote diff markdown → {}", outputFile);
        } catch (Exception e) {
            log.warn("RoseauApiExtractor: failed to write diff markdown: {}", e.getMessage());
        }
    }

    /**
     * Writes the body lines for a single breaking change using labels
     * appropriate to the specific kind of change.
     */
    private void appendBreakingChangeLines(StringBuilder sb, BreakingChange bc) {
        String impacted = bc.impactedSymbol().getQualifiedName();
        String newSym   = bc.newSymbol() != null ? bc.newSymbol().getQualifiedName() : null;

        switch (bc.kind()) {
            case TYPE_REMOVED ->
                sb.append("- REMOVED TYPE: `").append(impacted).append("`\n");
            case TYPE_NOW_PROTECTED ->
                sb.append("- TYPE (now protected, was public): `").append(impacted).append("`\n");
            case TYPE_KIND_CHANGED -> {
                sb.append("- TYPE (kind changed): `").append(impacted).append("`\n");
                if (newSym != null) sb.append("+ NEW KIND: `").append(newSym).append("`\n");
            }
            case TYPE_SUPERTYPE_REMOVED -> {
                sb.append("- TYPE: `").append(impacted).append("`\n");
                if (newSym != null) sb.append("  SUPERTYPE REMOVED: `").append(newSym).append("`\n");
            }
            case TYPE_NEW_ABSTRACT_METHOD -> {
                // impactedSymbol = the affected type; newSymbol = the new abstract method to implement
                sb.append("- TYPE: `").append(impacted).append("`\n");
                if (newSym != null)
                    sb.append("+ MUST NOW IMPLEMENT: `").append(newSym).append("`\n");
            }
            case CLASS_NOW_ABSTRACT ->
                sb.append("- CLASS (now abstract, was concrete): `").append(impacted).append("`\n");
            case CLASS_NOW_FINAL ->
                sb.append("- CLASS (now final, cannot be extended): `").append(impacted).append("`\n");
            case CLASS_NOW_CHECKED_EXCEPTION ->
                sb.append("- CLASS (now checked exception): `").append(impacted).append("`\n");
            case METHOD_REMOVED ->
                sb.append("- REMOVED: `").append(impacted).append("`\n");
            case METHOD_NOW_ABSTRACT ->
                sb.append("- METHOD (now abstract, was concrete): `").append(impacted).append("`\n");
            case METHOD_NOW_FINAL ->
                sb.append("- METHOD (now final, cannot be overridden): `").append(impacted).append("`\n");
            case METHOD_NOW_STATIC ->
                sb.append("- METHOD (now static): `").append(impacted).append("`\n");
            case METHOD_NO_LONGER_STATIC ->
                sb.append("- METHOD (no longer static): `").append(impacted).append("`\n");
            case METHOD_NOW_THROWS_CHECKED_EXCEPTION -> {
                sb.append("- METHOD: `").append(impacted).append("`\n");
                if (newSym != null) sb.append("+ NOW THROWS: `").append(newSym).append("`\n");
            }
            case METHOD_NO_LONGER_THROWS_CHECKED_EXCEPTION ->
                sb.append("- METHOD (no longer throws checked exception): `").append(impacted).append("`\n");
            case METHOD_RETURN_TYPE_ERASURE_CHANGED -> {
                sb.append("- OLD RETURN TYPE (erasure changed): `").append(impacted).append("`\n");
                if (newSym != null) sb.append("+ NEW RETURN TYPE: `").append(newSym).append("`\n");
            }
            case METHOD_RETURN_TYPE_CHANGED_INCOMPATIBLE -> {
                sb.append("- OLD RETURN TYPE (incompatible): `").append(impacted).append("`\n");
                if (newSym != null) sb.append("+ NEW RETURN TYPE: `").append(newSym).append("`\n");
            }
            case METHOD_NOW_PROTECTED ->
                sb.append("- METHOD (now protected, was public): `").append(impacted).append("`\n");
            case METHOD_OVERRIDABLE_NOW_STATIC ->
                sb.append("- METHOD (overridable, now static): `").append(impacted).append("`\n");
            case METHOD_PARAMETER_GENERICS_CHANGED ->
                sb.append("- METHOD (parameter generics changed): `").append(impacted).append("`\n");
            case CONSTRUCTOR_REMOVED ->
                sb.append("- REMOVED CONSTRUCTOR: `").append(impacted).append("`\n");
            case CONSTRUCTOR_NOW_PROTECTED ->
                sb.append("- CONSTRUCTOR (now protected, was public): `").append(impacted).append("`\n");
            case FIELD_REMOVED ->
                sb.append("- REMOVED FIELD: `").append(impacted).append("`\n");
            case FIELD_NOW_FINAL ->
                sb.append("- FIELD (now final): `").append(impacted).append("`\n");
            case FIELD_NOW_STATIC ->
                sb.append("- FIELD (now static): `").append(impacted).append("`\n");
            case FIELD_NO_LONGER_STATIC ->
                sb.append("- FIELD (no longer static): `").append(impacted).append("`\n");
            case FIELD_TYPE_ERASURE_CHANGED -> {
                sb.append("- OLD FIELD (type erasure changed): `").append(impacted).append("`\n");
                if (newSym != null) sb.append("+ NEW FIELD: `").append(newSym).append("`\n");
            }
            case FIELD_TYPE_CHANGED_INCOMPATIBLE -> {
                sb.append("- OLD FIELD (type incompatible): `").append(impacted).append("`\n");
                if (newSym != null) sb.append("+ NEW FIELD: `").append(newSym).append("`\n");
            }
            case FIELD_NOW_PROTECTED ->
                sb.append("- FIELD (now protected, was public): `").append(impacted).append("`\n");
            case CLASS_NOW_STATIC ->
                sb.append("- CLASS (now static): `").append(impacted).append("`\n");
            case CLASS_NO_LONGER_STATIC ->
                sb.append("- CLASS (no longer static): `").append(impacted).append("`\n");
            case ANNOTATION_TARGET_REMOVED ->
                sb.append("- ANNOTATION (target removed): `").append(impacted).append("`\n");
            case ANNOTATION_NEW_METHOD_WITHOUT_DEFAULT ->
                sb.append("- ANNOTATION (new method without default): `").append(impacted).append("`\n");
            case ANNOTATION_NO_LONGER_REPEATABLE ->
                sb.append("- ANNOTATION (no longer repeatable): `").append(impacted).append("`\n");
            case ANNOTATION_METHOD_NO_LONGER_DEFAULT ->
                sb.append("- ANNOTATION (method no longer has default): `").append(impacted).append("`\n");
            case FORMAL_TYPE_PARAMETER_ADDED ->
                sb.append("- FORMAL TYPE PARAMETER ADDED: `").append(impacted).append("`\n");
            case FORMAL_TYPE_PARAMETER_REMOVED ->
                sb.append("- FORMAL TYPE PARAMETER REMOVED: `").append(impacted).append("`\n");
            case FORMAL_TYPE_PARAMETER_CHANGED ->
                sb.append("- FORMAL TYPE PARAMETER CHANGED: `").append(impacted).append("`\n");
            default -> {
                sb.append("- `").append(impacted).append("`\n");
                if (newSym != null) sb.append("+ `").append(newSym).append("`\n");
            }
        }
    }

    /**
     * Generates roseau-api-v2.md — a human-readable specification of the new library API.
     * The agent can read this file to look up new method signatures and find alternative APIs
     * when fixing compilation errors caused by breaking changes.
     *
     * Format per type:
     * <pre>
     * ## [interface|class|enum] qualified.TypeName
     * extends SuperClass | implements Interface1, Interface2
     *
     * ### Fields
     * - `TYPE fieldName`
     *
     * ### Methods
     * - `MODIFIERS returnType methodName(paramType paramName, ...)`
     * </pre>
     */
    private void writeApiSpecMarkdown(API api, Path outputFile,
                                      String groupId, String artifactId, String newVersion) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("# API Specification: ").append(groupId).append(":").append(artifactId)
              .append(" ").append(newVersion).append("\n\n");
            sb.append("This file lists all exported types and their public API surface.\n");
            sb.append("Use it to look up correct method signatures when fixing compilation errors.\n\n");
            sb.append("---\n\n");

            List<TypeDecl> types = api.getExportedTypes().stream()
                    .filter(t -> t.isPublic() || t.isProtected())
                    .sorted(Comparator.comparing(TypeDecl::getQualifiedName))
                    .toList();

            for (TypeDecl type : types) {
                // Type header
                String kind = typeKind(type);
                sb.append("## ").append(kind).append(" `").append(type.getQualifiedName()).append("`\n");

                // Superclass (for classes)
                if (type instanceof ClassDecl classDecl && classDecl.getSuperClass() != null) {
                    String superName = classDecl.getSuperClass().getQualifiedName();
                    if (!superName.equals("java.lang.Object")) {
                        sb.append("extends `").append(superName).append("`  \n");
                    }
                }

                // Implemented interfaces
                Set<?> ifaces = type.getImplementedInterfaces();
                if (!ifaces.isEmpty()) {
                    String ifaceList = ifaces.stream()
                            .map(i -> "`" + i.toString() + "`")
                            .sorted()
                            .collect(Collectors.joining(", "));
                    sb.append("implements ").append(ifaceList).append("  \n");
                }
                sb.append("\n");

                // Fields
                List<FieldDecl> fields = type.getDeclaredFields().stream()
                        .filter(f -> f.isPublic() || f.isProtected())
                        .sorted(Comparator.comparing(FieldDecl::getSimpleName))
                        .toList();
                if (!fields.isEmpty()) {
                    sb.append("**Fields:**\n");
                    for (FieldDecl field : fields) {
                        sb.append("- `")
                          .append(modifiersStr(field.getModifiers()))
                          .append(field.getType().getQualifiedName()).append(" ")
                          .append(field.getSimpleName()).append("`\n");
                    }
                    sb.append("\n");
                }

                // Methods
                List<MethodDecl> methods = type.getDeclaredMethods().stream()
                        .filter(m -> m.isPublic() || m.isProtected())
                        .sorted(Comparator.comparing(MethodDecl::getSimpleName)
                                .thenComparing(MethodDecl::getSignature))
                        .toList();
                if (!methods.isEmpty()) {
                    sb.append("**Methods:**\n");
                    for (MethodDecl method : methods) {
                        String params = method.getParameters().stream()
                                .map(p -> p.type().getQualifiedName() + " " + p.name())
                                .collect(Collectors.joining(", "));
                        sb.append("- `")
                          .append(modifiersStr(method.getModifiers()))
                          .append(method.getType().getQualifiedName()).append(" ")
                          .append(method.getSimpleName()).append("(").append(params).append(")`\n");
                    }
                    sb.append("\n");
                }

                sb.append("---\n\n");
            }

            Files.writeString(outputFile, sb.toString(), StandardCharsets.UTF_8);
            log.info("RoseauApiExtractor: wrote API spec markdown ({} types) → {}", types.size(), outputFile);
        } catch (Exception e) {
            log.warn("RoseauApiExtractor: failed to write API spec markdown: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Formatting helpers
    // -------------------------------------------------------------------------

    private String typeKind(TypeDecl type) {
        if (type.isInterface())  return "interface";
        if (type.isEnum())       return "enum";
        if (type.isRecord())     return "record";
        if (type.isAnnotation()) return "@interface";
        return "class";
    }

    private String modifiersStr(Set<io.github.alien.roseau.api.model.Modifier> modifiers) {
        if (modifiers == null || modifiers.isEmpty()) return "";
        String mods = modifiers.stream()
                .map(m -> m.name().toLowerCase())
                .sorted()
                .collect(Collectors.joining(" "));
        return mods + " ";
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
