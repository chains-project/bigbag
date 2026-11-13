package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {

    private static final String STRING_CONTAINS = "StringContains";

    private static final String STRING_STARTS_WITH = "StringStartsWith";

    private static final String CONTAINS = "containsString";

    private static final String STARTS_WITH = "startsWith";

    private static final String CONTAINS_IC = "containsStringIgnoringCase";

    private static final String STARTS_WITH_IC = "startsWithIgnoringCase";

    public static void main(final String[] args) {
        final Path root = Paths.get(args.length > 0 ? args[0] : ".");
        try {
            final List<Path> sources = Files.walk(root)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
            for (Path source : sources) {
                transform(source);
            }
        } catch (final IOException err) {
            throw new IllegalStateException("Failed to transform sources", err);
        }
    }

    private static void transform(final Path source) throws IOException {
        final CompilationUnit cu = StaticJavaParser.parse(source);
        boolean changed = false;
        for (ObjectCreationExpr expr : cu.findAll(ObjectCreationExpr.class)) {
            final Expression replacement = replaceConstructor(expr);
            if (replacement != null) {
                expr.replace(replacement);
                changed = true;
            }
        }
        for (MethodCallExpr expr : cu.findAll(MethodCallExpr.class)) {
            final Expression replacement = replaceMethod(expr);
            if (replacement != null) {
                expr.replace(replacement);
                changed = true;
            }
        }
        if (normalizeImports(cu)) {
            changed = true;
        }
        if (changed) {
            Files.writeString(source, cu.toString());
        }
    }

    private static Expression replaceConstructor(final ObjectCreationExpr expr) {
        if (expr.getArguments().size() != 2) {
            return null;
        }
        final String type = expr.getType().asString();
        if (!STRING_CONTAINS.equals(type) && !type.endsWith('.' + STRING_CONTAINS)
            && !STRING_STARTS_WITH.equals(type) && !type.endsWith('.' + STRING_STARTS_WITH)) {
            return null;
        }
        return new MethodCallExpr(
            null,
            STRING_CONTAINS.equals(type) || type.endsWith('.' + STRING_CONTAINS) ? CONTAINS : STARTS_WITH,
            com.github.javaparser.ast.NodeList.nodeList(expr.getArguments().get(1).clone())
        );
    }

    private static Expression replaceMethod(final MethodCallExpr expr) {
        final String name = expr.getNameAsString();
        if (CONTAINS_IC.equals(name)) {
            return expr.clone().setName(CONTAINS);
        }
        if (STARTS_WITH_IC.equals(name)) {
            return expr.clone().setName(STARTS_WITH);
        }
        return null;
    }

    private static boolean normalizeImports(final CompilationUnit cu) {
        boolean changed = false;
        for (ImportDeclaration imp : cu.getImports().toArray(new ImportDeclaration[0])) {
            final String name = imp.getNameAsString();
            if (name.equals("org.hamcrest.core.StringContains." + CONTAINS_IC)
                || name.equals("org.hamcrest.core.StringStartsWith." + STARTS_WITH_IC)) {
                imp.remove();
                changed = true;
            }
        }
        if (!hasStaticImport(cu, "org.hamcrest.core.StringContains", CONTAINS)) {
            cu.addImport("org.hamcrest.core.StringContains." + CONTAINS, true, false);
            changed = true;
        }
        if (!hasStaticImport(cu, "org.hamcrest.core.StringStartsWith", STARTS_WITH)) {
            cu.addImport("org.hamcrest.core.StringStartsWith." + STARTS_WITH, true, false);
            changed = true;
        }
        return changed;
    }

    private static boolean hasStaticImport(final CompilationUnit cu, final String type, final String member) {
        for (ImportDeclaration imp : cu.getImports()) {
            if (imp.isStatic() && imp.getNameAsString().equals(type + '.' + member)) {
                return true;
            }
        }
        return false;
    }
}
