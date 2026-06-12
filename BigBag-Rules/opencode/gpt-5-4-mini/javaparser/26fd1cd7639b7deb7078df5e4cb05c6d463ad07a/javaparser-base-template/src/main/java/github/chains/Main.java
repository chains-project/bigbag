package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class Main {
    private static final String LEGACY_SCRIPT_RESULT = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String LEGACY_SCRIPT_RESULT_SIMPLE = "ScriptResult";
    private static final String REPLACEMENT_TYPE = "Object";

    public static void main(final String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-directory>");
        }

        Path sourceDirectory = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceDirectory)) {
            throw new IllegalArgumentException("Not a directory: " + sourceDirectory);
        }

        Files.walk(sourceDirectory)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .forEach(Main::transformFile);
    }

    private static void transformFile(final Path file) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(file, StandardCharsets.UTF_8);
            LexicalPreservingPrinter.setup(compilationUnit);

            boolean[] changed = {false};

            compilationUnit.findAll(MethodCallExpr.class).forEach(call -> {
                if ("getJavaScriptResult".equals(call.getNameAsString())
                        && call.getScope().filter(ObjectCreationExpr.class::isInstance).isPresent()) {
                    ObjectCreationExpr creation = call.getScope().map(ObjectCreationExpr.class::cast).orElseThrow();
                    if (isLegacyScriptResultCreation(creation)) {
                        Expression replacement = creation.getArgument(0).clone();
                        call.replace(replacement);
                        changed[0] = true;
                    }
                }
            });

            compilationUnit.findAll(ObjectCreationExpr.class).forEach(creation -> {
                if (isLegacyScriptResultCreation(creation)) {
                    Expression replacement = creation.getArgument(0).clone();
                    creation.replace(replacement);
                    changed[0] = true;
                }
            });

            compilationUnit.findAll(ClassOrInterfaceType.class).forEach(type -> {
                if (isLegacyScriptResultType(compilationUnit, type)) {
                    type.setName(REPLACEMENT_TYPE);
                    type.removeTypeArguments();
                    changed[0] = true;
                }
            });

            compilationUnit.findAll(Parameter.class).forEach(parameter -> {
                if (isLegacyScriptResultType(compilationUnit, parameter.getType())) {
                    parameter.setType(REPLACEMENT_TYPE);
                    changed[0] = true;
                }
            });

            compilationUnit.getImports().removeIf(importDecl -> {
                boolean legacy = LEGACY_SCRIPT_RESULT.equals(importDecl.getNameAsString());
                if (legacy) {
                    changed[0] = true;
                }
                return legacy;
            });

            if (changed[0]) {
                Files.writeString(file, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
            }
        }
        catch (Exception exception) {
            throw new IllegalStateException("Failed to transform " + file, exception);
        }
    }

    private static boolean isLegacyScriptResultCreation(final ObjectCreationExpr creation) {
        Type type = creation.getType();
        if (!(type instanceof ClassOrInterfaceType)) {
            return false;
        }
        return isLegacyScriptResultTypeName(type.asString());
    }

    private static boolean isLegacyScriptResultType(final CompilationUnit compilationUnit, final Type type) {
        if (!(type instanceof ClassOrInterfaceType)) {
            return false;
        }

        String typeName = type.asString();
        if (LEGACY_SCRIPT_RESULT.equals(typeName) || typeName.startsWith(LEGACY_SCRIPT_RESULT + "<")) {
            return hasLegacyScriptResultImport(compilationUnit);
        }

        return isLegacyScriptResultTypeName(typeName);
    }

    private static boolean isLegacyScriptResultTypeName(final String typeName) {
        return LEGACY_SCRIPT_RESULT.equals(typeName)
                || typeName.startsWith(LEGACY_SCRIPT_RESULT + ".")
                || typeName.endsWith("." + LEGACY_SCRIPT_RESULT_SIMPLE)
                || typeName.startsWith(LEGACY_SCRIPT_RESULT + "<")
                || typeName.startsWith(LEGACY_SCRIPT_RESULT_SIMPLE + "<");
    }

    private static boolean hasLegacyScriptResultImport(final CompilationUnit compilationUnit) {
        return compilationUnit.getImports().stream()
                .map(importDecl -> importDecl.getNameAsString())
                .anyMatch(LEGACY_SCRIPT_RESULT::equals);
    }
}
