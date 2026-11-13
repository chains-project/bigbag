package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_FQCN = "eu.europa.esig.dss.pades.CertificationPermission";
    private static final String NEW_FQCN = "eu.europa.esig.dss.enumerations.CertificationPermission";
    private static final String OLD_PACKAGE = "eu.europa.esig.dss.pades";

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: Main <source-root>");
            System.exit(1);
        }

        Path sourceRoot = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse source tree: " + sourceRoot, e);
        }
    }

    private static void rewriteFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file, StandardCharsets.UTF_8);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;

            changed |= rewriteImports(cu);
            changed |= rewriteQualifiedTypes(cu);
            changed |= rewriteQualifiedFieldAccesses(cu);

            if (changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to rewrite " + file, e);
        }
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;
        boolean hasNewImport = cu.getImports().stream()
                .anyMatch(importDeclaration -> importDeclaration.getNameAsString().equals(NEW_FQCN));
        boolean needsNewImport = false;

        for (ImportDeclaration importDeclaration : cu.getImports()) {
            if (importDeclaration.getNameAsString().equals(OLD_FQCN)) {
                importDeclaration.setName(NEW_FQCN);
                changed = true;
                hasNewImport = true;
            } else if (importDeclaration.isAsterisk() && importDeclaration.getNameAsString().equals(OLD_PACKAGE)) {
                needsNewImport = true;
            }
        }

        boolean hasOldTypeUsage = cu.findAll(ClassOrInterfaceType.class).stream()
                .anyMatch(type -> isOldQualifiedType(type.asString()));

        if ((needsNewImport || hasOldTypeUsage) && !hasNewImport) {
            cu.addImport(NEW_FQCN);
            changed = true;
        }

        return changed;
    }

    private static boolean rewriteQualifiedTypes(CompilationUnit cu) {
        boolean changed = false;
        List<ClassOrInterfaceType> types = cu.findAll(ClassOrInterfaceType.class);

        for (ClassOrInterfaceType type : types) {
            if (isOldQualifiedType(type.asString())) {
                type.replace(StaticJavaParser.parseClassOrInterfaceType(NEW_FQCN));
                changed = true;
            }
        }

        return changed;
    }

    private static boolean rewriteQualifiedFieldAccesses(CompilationUnit cu) {
        boolean changed = false;
        List<FieldAccessExpr> accesses = cu.findAll(FieldAccessExpr.class);

        for (FieldAccessExpr access : accesses) {
            if (OLD_FQCN.equals(access.getScope().toString())) {
                access.setScope(StaticJavaParser.parseExpression(NEW_FQCN));
                changed = true;
            }
        }

        return changed;
    }

    private static boolean isOldQualifiedType(String value) {
        return Objects.equals(value, OLD_FQCN);
    }

}
