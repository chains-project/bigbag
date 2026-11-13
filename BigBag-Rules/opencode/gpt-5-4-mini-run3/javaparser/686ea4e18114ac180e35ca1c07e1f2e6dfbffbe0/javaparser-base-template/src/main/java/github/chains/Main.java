package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String[] OLD_TO_NEW_IMPORTS = {
            "io.dropwizard.Application", "io.dropwizard.core.Application",
            "io.dropwizard.Configuration", "io.dropwizard.core.Configuration",
            "io.dropwizard.setup.Bootstrap", "io.dropwizard.core.setup.Bootstrap",
            "io.dropwizard.setup.Environment", "io.dropwizard.core.setup.Environment",
            "javax.validation", "jakarta.validation",
            "javax.ws.rs", "jakarta.ws.rs",
            "javax.servlet", "jakarta.servlet"
    };

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: Main <source-dir>");
        }
        Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("not a directory: " + root);
        }

        List<Path> files = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java")).forEach(files::add);
        }
        for (Path file : files) {
            transform(file);
        }
    }

    private static void transform(Path file) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(file);
        boolean changed = false;

        changed |= rewriteImports(cu);
        changed |= rewriteTypeNames(cu);
        changed |= rewriteStringsChecks(cu);
        changed |= removeNullableAnnotations(cu);

        if (changed) {
            Files.writeString(file, cu.toString());
        }
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;
        for (ImportDeclaration imp : new ArrayList<>(cu.getImports())) {
            String name = imp.getNameAsString();
            String replacement = null;
            for (int i = 0; i < OLD_TO_NEW_IMPORTS.length; i += 2) {
                String oldName = OLD_TO_NEW_IMPORTS[i];
                String newName = OLD_TO_NEW_IMPORTS[i + 1];
                if (name.equals(oldName)) {
                    replacement = newName;
                    break;
                }
                if (name.startsWith(oldName + ".")) {
                    replacement = newName + name.substring(oldName.length());
                    break;
                }
            }
            if (replacement != null && !replacement.equals(name)) {
                imp.setName(replacement);
                changed = true;
            }
            if (name.startsWith("javax.annotation.")) {
                imp.remove();
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteTypeNames(CompilationUnit cu) {
        boolean changed = false;
        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            String text = type.asString();
            String replacement = null;
            if (text.equals("io.dropwizard.Application")) {
                replacement = "io.dropwizard.core.Application";
            } else if (text.equals("io.dropwizard.Configuration")) {
                replacement = "io.dropwizard.core.Configuration";
            } else if (text.equals("io.dropwizard.setup.Bootstrap")) {
                replacement = "io.dropwizard.core.setup.Bootstrap";
            } else if (text.equals("io.dropwizard.setup.Environment")) {
                replacement = "io.dropwizard.core.setup.Environment";
            }
            if (replacement != null) {
                type.replace(StaticJavaParser.parseClassOrInterfaceType(replacement));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteStringsChecks(CompilationUnit cu) {
        boolean changed = false;
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (!"isNullOrEmpty".equals(call.getNameAsString()) || call.getArguments().size() != 1) {
                continue;
            }
            String scope = call.getScope().map(Object::toString).orElse("");
            if (!scope.endsWith("Strings")) {
                continue;
            }

            Expression value = call.getArgument(0).clone();
            Expression notNull = new BinaryExpr(value.clone(), new NullLiteralExpr(), BinaryExpr.Operator.NOT_EQUALS);
            Expression notEmpty = new UnaryExpr(new MethodCallExpr(value, "isEmpty"), UnaryExpr.Operator.LOGICAL_COMPLEMENT);
            Expression replacement = new BinaryExpr(notNull, notEmpty, BinaryExpr.Operator.AND);
            call.replace(replacement);
            changed = true;
        }
        return changed;
    }

    private static boolean removeNullableAnnotations(CompilationUnit cu) {
        boolean changed = false;
        for (AnnotationExpr annotation : new ArrayList<>(cu.findAll(AnnotationExpr.class))) {
            if ("Nullable".equals(annotation.getNameAsString())) {
                annotation.remove();
                changed = true;
            }
        }
        return changed;
    }
}
