package com.example.spoonanalyzer.analysis;

import com.example.spoonanalyzer.model.ConstructType;
import com.example.spoonanalyzer.model.ConstructUsage;
import com.example.spoonanalyzer.model.DependencyInfo;
import com.example.spoonanalyzer.model.DependencyOrigin;
import com.example.spoonanalyzer.resolution.DependencyResolver;
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
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        MavenLauncher launcher = createLauncher(normalizedRoot, false);
        CtModel model;

        try {
            LOGGER.info("Building Spoon model for project {}", normalizedRoot);
            model = launcher.buildModel();
        } catch (RuntimeException ex) {
            LOGGER.warn("Primary model build failed: {}. Retrying in no-classpath mode.", ex.getMessage());
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Full model build failure", ex);
            }
            launcher = createLauncher(normalizedRoot, true);
            model = launcher.buildModel();
        }

        LOGGER.info("Model built: {} top-level elements", model.getAllTypes().size());

        DependencyResolver dependencyResolver = new DependencyResolver(normalizedRoot, launcher.getEnvironment().getInputClassLoader());
        return new LineConstructAnalyzer(normalizedRoot, launcher, dependencyResolver);
    }

    private static MavenLauncher createLauncher(Path projectRoot, boolean noClasspath) {
        MavenLauncher launcher = new MavenLauncher(projectRoot.toString(), MavenLauncher.SOURCE_TYPE.ALL_SOURCE);
        launcher.getEnvironment().setNoClasspath(noClasspath);
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setCopyResources(false);
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

    public List<ConstructUsage> analyze(Path sourceFile, int lineNumber) {
        if (lineNumber < 1) {
            throw new IllegalArgumentException("Line number must be >= 1");
        }
        Path resolvedSource = resolveSource(sourceFile);
        LOGGER.info("Analyzing constructs for {}:{}", resolvedSource, lineNumber);

        CtCompilationUnit compilationUnit = findCompilationUnit(resolvedSource)
                .orElseThrow(() -> new IllegalArgumentException("Could not locate compilation unit for " + resolvedSource));

        Set<ConstructUsage> usages = new LinkedHashSet<>();
        String codeLine = readCodeLine(resolvedSource, lineNumber);
        usages.addAll(analyzeImports(compilationUnit, resolvedSource, lineNumber, codeLine));

        LineConstructScanner scanner = new LineConstructScanner(resolvedSource, lineNumber, dependencyResolver, codeLine);
        compilationUnit.getDeclaredTypes().forEach(scanner::scan);
        usages.addAll(scanner.getUsages());

        return new ArrayList<>(usages);
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

        if (reference instanceof CtTypeReference<?> typeReference) {
            dependencyInfo = dependencyResolver.resolveType(typeReference);
            signature = ConstructDescriptors.describeType(typeReference);
        } else if (reference instanceof CtExecutableReference<?> executableReference) {
            dependencyInfo = dependencyResolver.resolveExecutable(executableReference);
            signature = ConstructDescriptors.describeExecutable(executableReference);
        } else if (reference instanceof CtFieldReference<?> fieldReference) {
            dependencyInfo = dependencyResolver.resolveField(fieldReference);
            signature = ConstructDescriptors.describeField(fieldReference);
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

        return new ConstructUsage(ConstructType.IMPORT, signature, dependencyInfo, ctImport.getPosition(), codeLine);
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

    private String readCodeLine(Path sourceFile, int lineNumber) {
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
}

