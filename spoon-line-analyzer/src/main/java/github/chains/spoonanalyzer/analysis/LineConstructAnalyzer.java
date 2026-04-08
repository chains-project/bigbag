package github.chains.spoonanalyzer.analysis;

import github.chains.spoonanalyzer.model.ConstructType;
import github.chains.spoonanalyzer.model.ConstructUsage;
import github.chains.spoonanalyzer.model.DependencyInfo;
import github.chains.spoonanalyzer.model.DependencyOrigin;
import github.chains.spoonanalyzer.resolution.DependencyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.Launcher;
import spoon.MavenLauncher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class LineConstructAnalyzer {

    private static final Logger LOGGER = LoggerFactory.getLogger(LineConstructAnalyzer.class);

    private final Path projectRoot;
    private final Launcher launcher;
    private final DependencyResolver dependencyResolver;

    private LineConstructAnalyzer(Path projectRoot, Launcher launcher, DependencyResolver dependencyResolver) {
        this.projectRoot = projectRoot;
        this.launcher = launcher;
        this.dependencyResolver = dependencyResolver;
    }

    public static LineConstructAnalyzer initialize(Path projectRoot) {
        return initialize(projectRoot, Collections.emptyList());
    }

    public static LineConstructAnalyzer initialize(Path projectRoot, List<Path> manualClasspathEntries) {
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        List<Path> normalizedClasspath = normalizeClasspathEntries(manualClasspathEntries);
        MavenLauncher launcher = createLauncher(normalizedRoot, normalizedClasspath);
        CtModel model = launcher.buildModel();

        LOGGER.info("Model built for {}: {} top-level elements (manual cp entries: {})",
                normalizedRoot,
                model.getAllTypes().size(),
                normalizedClasspath.size());

        DependencyResolver dependencyResolver = new DependencyResolver(
                normalizedRoot,
                launcher.getEnvironment().getInputClassLoader());
        return new LineConstructAnalyzer(normalizedRoot, launcher, dependencyResolver);
    }

    private static MavenLauncher createLauncher(Path projectRoot, List<Path> manualClasspathEntries) {
        MavenLauncher launcher = new MavenLauncher(projectRoot.toString(), MavenLauncher.SOURCE_TYPE.ALL_SOURCE);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setCopyResources(false);
        applyManualClasspath(launcher, manualClasspathEntries);
        sanitizeClasspath(launcher);
        return launcher;
    }

    private static void sanitizeClasspath(MavenLauncher launcher) {
        String[] classpath = launcher.getEnvironment().getSourceClasspath();
        if (classpath == null || classpath.length == 0) {
            return;
        }

        List<String> sanitized = new ArrayList<>(classpath.length);
        boolean removedEntries = false;

        for (String entry : classpath) {
            if (entry == null || entry.isBlank()) {
                removedEntries = true;
                continue;
            }

            Path path;
            try {
                path = Path.of(entry);
            } catch (Exception ex) {
                LOGGER.debug("Skipping malformed classpath entry {}: {}", entry, ex.getMessage());
                removedEntries = true;
                continue;
            }

            if (!Files.exists(path)) {
                LOGGER.debug("Skipping non-existent classpath entry {}", path);
                removedEntries = true;
                continue;
            }

            if (Files.isDirectory(path)) {
                sanitized.add(path.toString());
                continue;
            }

            String lowerName = path.getFileName().toString().toLowerCase();
            if (lowerName.endsWith(".jar") || lowerName.endsWith(".zip") || lowerName.endsWith(".jmod")) {
                sanitized.add(path.toString());
                continue;
            }

            LOGGER.warn("Removing unsupported classpath entry {}", path);
            removedEntries = true;
        }

        if (removedEntries) {
            launcher.getEnvironment().setSourceClasspath(sanitized.toArray(String[]::new));
        }
    }

    private static void applyManualClasspath(MavenLauncher launcher, List<Path> manualClasspathEntries) {
        if (manualClasspathEntries == null || manualClasspathEntries.isEmpty()) {
            return;
        }

        List<String> classpath = new ArrayList<>();
        if (launcher.getEnvironment().getSourceClasspath() != null) {
            Collections.addAll(classpath, launcher.getEnvironment().getSourceClasspath());
        }

        for (Path entry : manualClasspathEntries) {
            if (entry == null) {
                continue;
            }
            Path normalized = entry.toAbsolutePath().normalize();
            if (!Files.exists(normalized)) {
                LOGGER.warn("Ignoring missing classpath entry {}", normalized);
                continue;
            }
            classpath.add(normalized.toString());
        }

        if (!classpath.isEmpty()) {
            launcher.getEnvironment().setSourceClasspath(classpath.toArray(String[]::new));
        }
    }

    public List<ConstructUsage> analyze(Path sourceFile, int lineNumber) {
        if (lineNumber < 1) {
            throw new IllegalArgumentException("Line number must be >= 1");
        }
        Path resolvedSource = resolveSource(sourceFile);
        LOGGER.info("Analyzing constructs for {}:{}", resolvedSource, lineNumber);

        // Try to use the full project model first (better for resolving references)
        // Only build model for single file if the file is not in the main model
        CtCompilationUnit compilationUnit = findCompilationUnit(resolvedSource)
                .orElseGet(() -> {
                    LOGGER.debug("File {} not found in main model, building model for file only", resolvedSource);
                    return buildModelForFile(resolvedSource);
                });

        Set<ConstructUsage> usages = new LinkedHashSet<>();
        String codeLine = readCodeLineFromSpoon(compilationUnit, lineNumber);
        usages.addAll(analyzeImports(compilationUnit, resolvedSource, lineNumber, codeLine));

        LineConstructScanner scanner = new LineConstructScanner(resolvedSource, lineNumber, dependencyResolver, codeLine);
        compilationUnit.getDeclaredTypes().forEach(scanner::scan);
        usages.addAll(scanner.getUsages());

        return new ArrayList<>(usages);
    }

    /**
     * Builds a Spoon model only for the specified file, not the entire project.
     * This is more efficient when analyzing individual files.
     */
    private CtCompilationUnit buildModelForFile(Path sourceFile) {
        // Try to find existing compilation unit first
        Optional<CtCompilationUnit> existing = findCompilationUnit(sourceFile);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Build model only for this file using a new launcher
        spoon.Launcher fileLauncher = new spoon.Launcher();
        fileLauncher.getEnvironment().setNoClasspath(true);
        fileLauncher.getEnvironment().setIgnoreSyntaxErrors(true);
        fileLauncher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        fileLauncher.getEnvironment().setAutoImports(true);
        fileLauncher.getEnvironment().setCommentEnabled(false);
        fileLauncher.getEnvironment().setCopyResources(false);

        // Copy classpath from main launcher
        if (launcher.getEnvironment().getSourceClasspath() != null) {
            fileLauncher.getEnvironment().setSourceClasspath(launcher.getEnvironment().getSourceClasspath());
        }

        // Add only this file as input
        fileLauncher.addInputResource(sourceFile.toString());
        fileLauncher.buildModel();

        // Find the compilation unit we just built
        @SuppressWarnings("deprecation")
        Map<String, spoon.reflect.cu.CompilationUnit> compilationUnitMap = fileLauncher.getFactory().CompilationUnit().getMap();
        Collection<spoon.reflect.cu.CompilationUnit> values = compilationUnitMap.values();
        for (spoon.reflect.cu.CompilationUnit deprecatedUnit : values) {
            CtCompilationUnit unit = (CtCompilationUnit) deprecatedUnit;
            if (unit.getFile() != null) {
                Path unitPath = unit.getFile().toPath().toAbsolutePath().normalize();
                if (unitPath.equals(sourceFile.toAbsolutePath().normalize())) {
                    return unit;
                }
            }
        }

        throw new IllegalArgumentException("Failed to build model for " + sourceFile);
    }

    private Path resolveSource(Path sourceFile) {
        Path path = sourceFile.isAbsolute() ? sourceFile : projectRoot.resolve(sourceFile);
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.exists(normalized)) {
            throw new IllegalArgumentException("Source file does not exist: " + normalized);
        }
        return normalized;
    }

    @SuppressWarnings("deprecation")
    private Optional<CtCompilationUnit> findCompilationUnit(Path sourceFile) {
        // Note: Factory still returns deprecated CompilationUnit, but it's compatible with CtCompilationUnit
        // We suppress deprecation warnings here because the Factory API hasn't been updated yet
        Map<String, spoon.reflect.cu.CompilationUnit> compilationUnitMap = launcher.getFactory().CompilationUnit().getMap();
        Collection<spoon.reflect.cu.CompilationUnit> values = compilationUnitMap.values();
        for (spoon.reflect.cu.CompilationUnit deprecatedUnit : values) {
            // CompilationUnit extends CtCompilationUnit, so we can cast safely
            CtCompilationUnit unit = (CtCompilationUnit) deprecatedUnit;
            if (unit.getFile() != null) {
                Path unitPath = unit.getFile().toPath().toAbsolutePath().normalize();
                if (unitPath.equals(sourceFile)) {
                    return Optional.of(unit);
                }
            }
        }
        return Optional.empty();
    }

    private List<ConstructUsage> analyzeImports(CtCompilationUnit compilationUnit,
                                                Path sourceFile,
                                                int lineNumber,
                                                String codeLine) {
        List<ConstructUsage> usages = new ArrayList<>();
        for (CtImport ctImport : compilationUnit.getImports()) {
            if (ctImport == null || ctImport.getPosition() == null) {
                continue;
            }
            if (!matchesTargetLine(sourceFile, ctImport, lineNumber)) {
                continue;
            }
            usages.add(createImportUsage(ctImport, codeLine));
        }
        return usages;
    }

    private boolean matchesTargetLine(Path sourceFile, CtElement element, int lineNumber) {
        if (element.getPosition() == null || !element.getPosition().isValidPosition()) {
            return false;
        }
        if (element.getPosition().getFile() == null) {
            return false;
        }
        Path elementPath = element.getPosition().getFile().toPath().toAbsolutePath().normalize();
        if (!elementPath.equals(sourceFile)) {
            return false;
        }
        int begin = element.getPosition().getLine();
        int end = element.getPosition().getEndLine();
        return lineNumber >= begin && lineNumber <= end;
    }

    private ConstructUsage createImportUsage(CtImport ctImport, String codeLine) {
        CtReference reference = ctImport.getReference();
        DependencyInfo dependencyInfo;
        String signature;
        String fqn = null;

        if (reference instanceof CtTypeReference<?> typeReference) {
            dependencyInfo = dependencyResolver.resolveType(typeReference);
            signature = ConstructDescriptors.describeType(typeReference);
            fqn = typeReference.getQualifiedName();
        } else if (reference instanceof CtExecutableReference<?> executableReference) {
            dependencyInfo = dependencyResolver.resolveExecutable(executableReference);
            signature = ConstructDescriptors.describeExecutable(executableReference);
            fqn = executableReference.getDeclaringType() != null
                    ? executableReference.getDeclaringType().getQualifiedName()
                    : null;
        } else if (reference instanceof CtFieldReference<?> fieldReference) {
            dependencyInfo = dependencyResolver.resolveField(fieldReference);
            signature = ConstructDescriptors.describeField(fieldReference);
            fqn = fieldReference.getDeclaringType() != null
                    ? fieldReference.getDeclaringType().getQualifiedName()
                    : null;
        } else if (reference instanceof CtPackageReference packageReference) {
            dependencyInfo = DependencyInfo.builder(DependencyOrigin.UNKNOWN).build();
            signature = packageReference.getQualifiedName() + ".*";
        } else if (reference != null) {
            dependencyInfo = DependencyInfo.builder(DependencyOrigin.UNKNOWN).build();
            signature = reference.toString();
        } else {
            dependencyInfo = DependencyInfo.builder(DependencyOrigin.UNKNOWN).build();
            signature = "<unknown import>";
        }

        if ("<unknown import>".equals(signature)) {
            String fallbackSignature = extractImportSignature(ctImport);
            if (fallbackSignature != null && !fallbackSignature.isBlank()) {
                signature = fallbackSignature;
            }
        }

        return new ConstructUsage(ConstructType.IMPORT, signature, dependencyInfo, ctImport.getPosition(), codeLine, fqn);
    }

    private String extractImportSignature(CtImport ctImport) {
        if (ctImport == null || ctImport.getPosition() == null) {
            return null;
        }
        if (ctImport.getPosition().getFile() == null) {
            return null;
        }
        Path filePath = ctImport.getPosition().getFile().toPath();
        int startLine = Math.max(1, ctImport.getPosition().getLine());
        int endLine = Math.max(startLine, ctImport.getPosition().getEndLine());
        try {
            List<String> lines = Files.readAllLines(filePath);
            if (lines.isEmpty()) {
                return null;
            }
            StringBuilder snippet = new StringBuilder();
            for (int line = startLine; line <= endLine && line <= lines.size(); line++) {
                if (snippet.length() > 0) {
                    snippet.append(' ');
                }
                snippet.append(lines.get(line - 1));
            }
            String raw = snippet.toString().trim();
            if (raw.isEmpty()) {
                return null;
            }
            if (raw.startsWith("import")) {
                raw = raw.substring("import".length()).trim();
            }
            if (raw.startsWith("static")) {
                raw = raw.substring("static".length()).trim();
            }
            if (raw.endsWith(";")) {
                raw = raw.substring(0, raw.length() - 1).trim();
            }
            return raw.isEmpty() ? null : raw;
        } catch (IOException ex) {
            LOGGER.debug("Failed to extract import signature for {}: {}", filePath, ex.getMessage());
            return null;
        }
    }

    /**
     * Reads a specific line from the source code using Spoon's compilation unit.
     * This uses Spoon's already-loaded source code instead of reading from the file system.
     *
     * @param compilationUnit The Spoon compilation unit containing the source code
     * @param lineNumber The 1-based line number to read
     * @return The line content, or null if the line number is invalid or source code is unavailable
     */
    private String readCodeLineFromSpoon(CtCompilationUnit compilationUnit, int lineNumber) {
        if (lineNumber < 1 || compilationUnit == null) {
            return null;
        }

        try {
            // Get the original source code from Spoon's compilation unit
            String originalSourceCode = compilationUnit.getOriginalSourceCode();
            if (originalSourceCode == null || originalSourceCode.isEmpty()) {
                // Fallback: try to read from file if Spoon doesn't have the source
                Path sourceFile = compilationUnit.getFile() != null 
                    ? compilationUnit.getFile().toPath() 
                    : null;
                if (sourceFile != null) {
                    return readCodeLineFromFile(sourceFile, lineNumber);
                }
                return null;
            }

            // Split by line separators (handles both \n and \r\n)
            String[] lines = originalSourceCode.split("\r?\n", -1);
            if (lineNumber > lines.length) {
                return null;
            }

            String line = lines[lineNumber - 1];
            // Normalize tabs and trailing whitespace
            return line.replace("\t", "    ").stripTrailing();
        } catch (Exception ex) {
            LOGGER.debug("Failed to read line {} from Spoon compilation unit: {}", lineNumber, ex.getMessage());
            // Fallback to file reading if Spoon source access fails
            Path sourceFile = compilationUnit.getFile() != null 
                ? compilationUnit.getFile().toPath() 
                : null;
            if (sourceFile != null) {
                return readCodeLineFromFile(sourceFile, lineNumber);
            }
            return null;
        }
    }

    /**
     * Fallback method to read a line directly from the file system.
     * Used when Spoon's source code access is unavailable.
     *
     * @param sourceFile The source file path
     * @param lineNumber The 1-based line number to read
     * @return The line content, or null if reading fails
     */
    private String readCodeLineFromFile(Path sourceFile, int lineNumber) {
        if (lineNumber < 1) {
            return null;
        }
        try (Stream<String> lines = Files.lines(sourceFile)) {
            return lines
                    .skip(lineNumber - 1L)
                    .findFirst()
                    .map(line -> line.replace("\t", "    ").stripTrailing())
                    .orElse(null);
        } catch (IOException ex) {
            LOGGER.debug("Failed to read line {} from {}: {}", lineNumber, sourceFile, ex.getMessage());
            return null;
        }
    }

    private static List<Path> normalizeClasspathEntries(List<Path> entries) {
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }
        List<Path> normalized = new ArrayList<>(entries.size());
        for (Path entry : entries) {
            if (entry == null) {
                continue;
            }
            Path normalizedEntry = entry.toAbsolutePath().normalize();
            if (!normalized.contains(normalizedEntry)) {
                normalized.add(normalizedEntry);
            }
        }
        return Collections.unmodifiableList(normalized);
    }
}

