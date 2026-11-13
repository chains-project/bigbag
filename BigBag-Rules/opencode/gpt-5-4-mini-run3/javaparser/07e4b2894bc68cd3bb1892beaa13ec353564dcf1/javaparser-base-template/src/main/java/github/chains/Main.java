package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

public class Main {
    private static final String DEFAULT_OLD_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String DEFAULT_NEW_TYPE = "java.lang.Object";

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: Main <source-root> [old-fqcn] [new-fqcn]");
            System.exit(1);
        }

        Path sourceRoot = Paths.get(args[0]);
        String oldType = args.length > 1 ? args[1] : DEFAULT_OLD_TYPE;
        String newType = args.length > 2 ? args[2] : DEFAULT_NEW_TYPE;

        try {
            transform(sourceRoot, oldType, newType);
        }
        catch (IOException exception) {
            throw new RuntimeException("Failed to transform sources under " + sourceRoot, exception);
        }
    }

    static void transform(Path sourceRoot, String oldType, String newType) throws IOException {
        if (!Files.exists(sourceRoot)) {
            throw new IOException("Source root does not exist: " + sourceRoot);
        }

        List<Path> files = Files.walk(sourceRoot)
                .filter(path -> path.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .toList();

        for (Path file : files) {
            rewriteFile(file, oldType, newType);
        }
    }

    private static void rewriteFile(Path file, String oldType, String newType) throws IOException {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        CompilationUnit compilationUnit = StaticJavaParser.parse(source);
        LexicalPreservingPrinter.setup(compilationUnit);

        boolean changed = replaceImports(compilationUnit, oldType, newType);
        changed |= replaceScriptResultPatterns(compilationUnit, oldType);
        changed |= replaceTypeUsages(compilationUnit, oldType, newType);

        if (changed) {
            Files.writeString(file, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
        }
    }

    private static boolean replaceImports(CompilationUnit compilationUnit, String oldType, String newType) {
        boolean changed = false;

        NodeList<ImportDeclaration> imports = compilationUnit.getImports();
        for (int i = imports.size() - 1; i >= 0; i--) {
            ImportDeclaration importDeclaration = imports.get(i);
            String importedName = importDeclaration.getNameAsString();

            if (isScriptResultTypeName(importedName) || importedName.equals(newType) || importedName.equals(packageName(oldType))) {
                importDeclaration.remove();
                changed = true;
            }
        }

        return changed;
    }

    private static boolean replaceTypeUsages(CompilationUnit compilationUnit, String oldType, String newType) {
        boolean changed = false;

        for (com.github.javaparser.ast.body.VariableDeclarator variableDeclarator : compilationUnit.findAll(com.github.javaparser.ast.body.VariableDeclarator.class)) {
            if (matchesTypeReference(variableDeclarator.getType(), oldType) || isScriptResultType(variableDeclarator.getType())) {
                variableDeclarator.setType(StaticJavaParser.parseType(newType));
                changed = true;
            }
        }

        for (ClassOrInterfaceType type : compilationUnit.findAll(ClassOrInterfaceType.class)) {
            if (matchesTypeReference(type, oldType) || isScriptResultType(type)) {
                type.replace(StaticJavaParser.parseClassOrInterfaceType(newType));
                changed = true;
            }
        }

        for (ObjectCreationExpr creationExpr : compilationUnit.findAll(ObjectCreationExpr.class)) {
            if (matchesTypeReference(creationExpr.getType(), oldType) || isScriptResultType(creationExpr.getType())) {
                creationExpr.setType(StaticJavaParser.parseClassOrInterfaceType(newType));
                changed = true;
            }
        }

        return changed;
    }

    private static boolean replaceScriptResultPatterns(CompilationUnit compilationUnit, String oldType) {
        boolean changed = false;

        for (MethodCallExpr methodCallExpr : compilationUnit.findAll(MethodCallExpr.class)) {
            if (methodCallExpr.getNameAsString().equals("getJavaScriptResult")
                    && methodCallExpr.getScope().isPresent()
                    && methodCallExpr.getScope().get() instanceof ObjectCreationExpr) {
                ObjectCreationExpr creationExpr = (ObjectCreationExpr) methodCallExpr.getScope().get();
                if ((matchesTypeReference(creationExpr, oldType) || isScriptResultType(creationExpr.getType()))
                        && !creationExpr.getArguments().isEmpty()) {
                    Expression replacement = creationExpr.getArgument(0).clone();
                    methodCallExpr.replace(replacement);
                    changed = true;
                }
                else if ((matchesTypeReference(creationExpr, oldType) || isScriptResultType(creationExpr.getType()))
                        && creationExpr.getArguments().isEmpty()) {
                    methodCallExpr.replace(creationExpr.clone());
                    changed = true;
                }
            }
        }

        for (ObjectCreationExpr creationExpr : compilationUnit.findAll(ObjectCreationExpr.class)) {
            if ((matchesTypeReference(creationExpr, oldType) || isScriptResultType(creationExpr.getType()))
                    && !creationExpr.getArguments().isEmpty()) {
                creationExpr.replace(creationExpr.getArgument(0).clone());
                changed = true;
            }
        }

        return changed;
    }

    private static boolean matchesTypeReference(ObjectCreationExpr creationExpr, String oldType) {
        String text = creationExpr.getType().asString();
        return text.equals(oldType) || text.equals(simpleName(oldType));
    }

    private static boolean matchesTypeReference(ClassOrInterfaceType type, String oldType) {
        String text = type.asString();
        return text.equals(oldType) || text.equals(simpleName(oldType));
    }

    private static boolean matchesTypeReference(Type type, String oldType) {
        String text = type.asString();
        return text.equals(oldType) || text.equals(simpleName(oldType));
    }

    private static boolean isScriptResultType(Type type) {
        return isScriptResultTypeName(type.asString());
    }

    private static boolean isScriptResultTypeName(String text) {
        return text.equals("com.gargoylesoftware.htmlunit.ScriptResult")
                || text.equals("org.htmlunit.ScriptResult")
                || text.equals("ScriptResult");
    }

    private static String packageName(String fqcn) {
        int lastDot = fqcn.lastIndexOf('.');
        return lastDot < 0 ? "" : fqcn.substring(0, lastDot);
    }

    private static String simpleName(String fqcn) {
        int lastDot = fqcn.lastIndexOf('.');
        return lastDot < 0 ? fqcn : fqcn.substring(lastDot + 1);
    }
}
