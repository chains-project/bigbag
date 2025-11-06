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
import spoon.reflect.cu.CompilationUnit;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
        return launcher;
    }

    public List<ConstructUsage> analyze(Path sourceFile, int lineNumber) {
        if (lineNumber < 1) {
            throw new IllegalArgumentException("Line number must be >= 1");
        }
        Path resolvedSource = resolveSource(sourceFile);
        LOGGER.info("Analyzing constructs for {}:{}", resolvedSource, lineNumber);

        CompilationUnit compilationUnit = findCompilationUnit(resolvedSource)
                .orElseThrow(() -> new IllegalArgumentException("Could not locate compilation unit for " + resolvedSource));

        Set<ConstructUsage> usages = new LinkedHashSet<>();
        usages.addAll(analyzeImports(compilationUnit, resolvedSource, lineNumber));

        LineConstructScanner scanner = new LineConstructScanner(resolvedSource, lineNumber, dependencyResolver);
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

    private Optional<CompilationUnit> findCompilationUnit(Path sourceFile) {
        Map<String, CompilationUnit> compilationUnitMap = launcher.getFactory().CompilationUnit().getMap();
        Collection<CompilationUnit> values = compilationUnitMap.values();
        for (CompilationUnit unit : values) {
            if (unit.getFile() != null) {
                Path unitPath = unit.getFile().toPath().toAbsolutePath().normalize();
                if (unitPath.equals(sourceFile)) {
                    return Optional.of(unit);
                }
            }
        }
        return Optional.empty();
    }

    private List<ConstructUsage> analyzeImports(CompilationUnit compilationUnit, Path sourceFile, int lineNumber) {
        List<ConstructUsage> usages = new ArrayList<>();
        for (CtImport ctImport : compilationUnit.getImports()) {
            if (ctImport == null || ctImport.getPosition() == null) {
                continue;
            }
            if (!matchesTargetLine(sourceFile, ctImport, lineNumber)) {
                continue;
            }
            usages.add(createImportUsage(ctImport));
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

    private ConstructUsage createImportUsage(CtImport ctImport) {
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

        return new ConstructUsage(ConstructType.IMPORT, signature, dependencyInfo, ctImport.getPosition());
    }
}

