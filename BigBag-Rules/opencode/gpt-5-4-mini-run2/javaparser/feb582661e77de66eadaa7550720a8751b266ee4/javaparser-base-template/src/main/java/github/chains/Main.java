package github.chains;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    private static final Map<String, String> RENAMED_TYPES = new LinkedHashMap<String, String>();
    private static final List<String> SINGLETON_TYPES = Arrays.asList(
        "liquibase.change.ChangeFactory",
        "liquibase.configuration.LiquibaseConfiguration",
        "liquibase.database.ConnectionServiceFactory",
        "liquibase.database.DatabaseFactory",
        "liquibase.datatype.DataTypeFactory",
        "liquibase.diff.compare.DatabaseObjectComparatorFactory",
        "liquibase.diff.DiffGeneratorFactory",
        "liquibase.diff.output.changelog.ChangeGeneratorFactory",
        "liquibase.executor.ExecutorService",
        "liquibase.lockservice.LockServiceFactory",
        "liquibase.logging.LogFactory",
        "liquibase.parser.ChangeLogParserFactory",
        "liquibase.parser.NamespaceDetailsFactory",
        "liquibase.parser.SnapshotParserFactory",
        "liquibase.precondition.PreconditionFactory",
        "liquibase.serializer.ChangeLogSerializerFactory",
        "liquibase.serializer.ReflectionSerializer",
        "liquibase.serializer.SnapshotSerializerFactory",
        "liquibase.snapshot.SnapshotGeneratorFactory",
        "liquibase.snapshot.SnapshotIdService",
        "liquibase.sql.visitor.SqlVisitorFactory",
        "liquibase.sqlgenerator.SqlGeneratorFactory",
        "liquibase.structure.core.DatabaseObjectFactory"
    );

    static {
        RENAMED_TYPES.put("liquibase.util.StringUtils", "liquibase.util.StringUtil");
    }

    public static void main(String[] args) throws IOException {
        Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        Path outputRoot = Paths.get(args.length > 1 ? args[1] : sourceRoot.toString()).toAbsolutePath().normalize();

        StaticJavaParser.getConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_8);

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            List<Path> javaFiles = paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .collect(Collectors.toList());

            for (Path inputFile : javaFiles) {
                Path relative = sourceRoot.relativize(inputFile);
                Path outputFile = outputRoot.resolve(relative);
                rewriteFile(inputFile, outputFile);
            }
        }
    }

    private static void rewriteFile(Path inputFile, Path outputFile) throws IOException {
        String source = new String(Files.readAllBytes(inputFile), StandardCharsets.UTF_8);
        CompilationUnit compilationUnit;
        try {
            compilationUnit = StaticJavaParser.parse(source);
        } catch (ParseProblemException ex) {
            throw new IOException("Failed to parse " + inputFile, ex);
        }

        LexicalPreservingPrinter.setup(compilationUnit);
        boolean changed = false;

        for (Map.Entry<String, String> entry : RENAMED_TYPES.entrySet()) {
            changed |= renameImportedType(compilationUnit, entry.getKey(), entry.getValue());
            changed |= replaceSimpleName(compilationUnit, entry.getKey().substring(entry.getKey().lastIndexOf('.') + 1), entry.getValue().substring(entry.getValue().lastIndexOf('.') + 1));
        }

        changed |= rewriteSingletonAccessors(compilationUnit);
        changed |= addLiquibaseServiceAnnotations(compilationUnit);

        if (changed) {
            Files.createDirectories(outputFile.getParent());
            Files.write(outputFile, LexicalPreservingPrinter.print(compilationUnit).getBytes(StandardCharsets.UTF_8));
        } else if (!inputFile.equals(outputFile)) {
            Files.createDirectories(outputFile.getParent());
            Files.write(outputFile, source.getBytes(StandardCharsets.UTF_8));
        } else if (!Files.exists(outputFile)) {
            Files.write(outputFile, source.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static boolean renameImportedType(CompilationUnit compilationUnit, String oldType, String newType) {
        boolean changed = false;

        for (ImportDeclaration importDeclaration : compilationUnit.getImports()) {
            if (importDeclaration.getNameAsString().equals(oldType)) {
                importDeclaration.setName(newType);
                changed = true;
            }
        }

        return changed;
    }

    private static boolean replaceSimpleName(CompilationUnit compilationUnit, String oldName, String newName) {
        boolean changed = false;

        for (NameExpr nameExpr : compilationUnit.findAll(NameExpr.class)) {
            if (nameExpr.getNameAsString().equals(oldName)) {
                nameExpr.setName(newName);
                changed = true;
            }
        }

        for (FieldAccessExpr fieldAccessExpr : compilationUnit.findAll(FieldAccessExpr.class)) {
            if (fieldAccessExpr.getNameAsString().equals(oldName)) {
                fieldAccessExpr.setName(newName);
                changed = true;
            }
        }

        return changed;
    }

    private static boolean rewriteSingletonAccessors(CompilationUnit compilationUnit) {
        boolean changed = false;

        for (MethodCallExpr methodCallExpr : compilationUnit.findAll(MethodCallExpr.class)) {
            if (!methodCallExpr.getNameAsString().equals("getInstance") || methodCallExpr.getArguments().size() != 0 || !methodCallExpr.getScope().isPresent()) {
                continue;
            }

            String scopeText = methodCallExpr.getScope().get().toString();
            String singletonType = matchSingletonType(scopeText, compilationUnit);
            if (singletonType == null) {
                continue;
            }

            String replacement = "liquibase.Scope.getCurrentScope().getSingleton(" + singletonType + ".class)";
            methodCallExpr.replace(StaticJavaParser.parseExpression(replacement));
            changed = true;
        }

        return changed;
    }

    private static boolean addLiquibaseServiceAnnotations(CompilationUnit compilationUnit) {
        boolean changed = false;

        for (ClassOrInterfaceDeclaration declaration : compilationUnit.findAll(ClassOrInterfaceDeclaration.class)) {
            if (declaration.isInterface() || declaration.isAnnotationDeclaration()) {
                continue;
            }
            if (declaration.getAnnotationByName("LiquibaseService").isPresent()) {
                continue;
            }
            if (shouldRegisterAsService(compilationUnit, declaration)) {
                compilationUnit.addImport("liquibase.servicelocator.LiquibaseService");
                declaration.addAnnotation(new MarkerAnnotationExpr("LiquibaseService"));
                changed = true;
            }
        }

        return changed;
    }

    private static boolean shouldRegisterAsService(CompilationUnit compilationUnit, ClassOrInterfaceDeclaration declaration) {
        List<String> extendedTypes = declaration.getExtendedTypes().stream()
            .map(type -> resolveTypeName(compilationUnit, type.toString()))
            .collect(Collectors.toList());

        List<String> implementedTypes = declaration.getImplementedTypes().stream()
            .map(type -> resolveTypeName(compilationUnit, type.toString()))
            .collect(Collectors.toList());

        List<String> allTypes = new java.util.ArrayList<String>(extendedTypes);
        allTypes.addAll(implementedTypes);

        return matchesTypePrefix(allTypes, "liquibase.database.core.")
            || matchesTypePrefix(allTypes, "liquibase.sqlgenerator.core.")
            || matchesTypePrefix(allTypes, "liquibase.change.core.")
            || matchesExactType(allTypes, "liquibase.change.AbstractChange")
            || matchesExactType(allTypes, "liquibase.sqlgenerator.core.AbstractSqlGenerator");
    }

    private static String normalizeTypeName(String typeName) {
        int genericStart = typeName.indexOf('<');
        return genericStart >= 0 ? typeName.substring(0, genericStart) : typeName;
    }

    private static String resolveTypeName(CompilationUnit compilationUnit, String typeName) {
        String normalized = normalizeTypeName(typeName);
        if (normalized.contains(".")) {
            return normalized;
        }

        for (ImportDeclaration importDeclaration : compilationUnit.getImports()) {
            String importedName = importDeclaration.getNameAsString();
            if (importedName.endsWith("." + normalized)) {
                return importedName;
            }
        }

        if (compilationUnit.getPackageDeclaration().isPresent()) {
            return compilationUnit.getPackageDeclaration().get().getNameAsString() + "." + normalized;
        }

        return normalized;
    }

    private static boolean matchesTypePrefix(List<String> types, String prefix) {
        for (String type : types) {
            if (type.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesExactType(List<String> types, String fqcn) {
        for (String type : types) {
            if (type.equals(fqcn) || type.endsWith("." + fqcn.substring(fqcn.lastIndexOf('.') + 1))) {
                return true;
            }
        }
        return false;
    }

    private static String matchSingletonType(String scopeText, CompilationUnit compilationUnit) {
        for (String fqcn : SINGLETON_TYPES) {
            String simpleName = fqcn.substring(fqcn.lastIndexOf('.') + 1);
            if (scopeText.equals(fqcn) || scopeText.equals(simpleName) || isImportedAs(compilationUnit, fqcn, simpleName)) {
                return fqcn;
            }
        }

        return null;
    }

    private static boolean isImportedAs(CompilationUnit compilationUnit, String fqcn, String simpleName) {
        Set<String> importedNames = compilationUnit.getImports().stream()
            .map(importDeclaration -> importDeclaration.getNameAsString())
            .collect(Collectors.toSet());
        return importedNames.contains(fqcn) || importedNames.stream().anyMatch(name -> name.endsWith("." + simpleName));
    }
}
