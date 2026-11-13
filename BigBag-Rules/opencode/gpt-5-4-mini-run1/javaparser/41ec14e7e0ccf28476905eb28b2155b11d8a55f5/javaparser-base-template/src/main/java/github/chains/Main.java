package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class Main {
    private static final Map<String, String> TYPE_RENAMES = new LinkedHashMap<>();

    static {
        TYPE_RENAMES.put("com.premiumminds.webapp.wicket.BootstrapControlGroupFeedback", "com.premiumminds.webapp.wicket.bootstrap.BootstrapControlGroupFeedback");
        TYPE_RENAMES.put("com.premiumminds.webapp.wicket.BootstrapFeedbackPanel", "com.premiumminds.webapp.wicket.bootstrap.BootstrapFeedbackPanel");
        TYPE_RENAMES.put("com.premiumminds.webapp.wicket.BootstrapFeedbackPopover", "com.premiumminds.webapp.wicket.bootstrap.BootstrapFeedbackPopover");
        TYPE_RENAMES.put("com.premiumminds.webapp.wicket.BootstrapModal", "com.premiumminds.webapp.wicket.bootstrap.BootstrapModal");
        TYPE_RENAMES.put("com.premiumminds.webapp.wicket.BootstrapTemporalDatepicker", "com.premiumminds.webapp.wicket.bootstrap.BootstrapTemporalDatepicker");
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("expected a source directory path");
        }

        Path root = Path.of(args[0]);
        try {
            Files.walk(root)
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void rewriteFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path, StandardCharsets.UTF_8);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;
            changed |= rewriteImports(cu);
            changed |= rewriteTypes(cu);

            if (changed) {
                Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("failed to rewrite " + path, e);
        }
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;
        for (ImportDeclaration importDeclaration : cu.getImports()) {
            String name = importDeclaration.getNameAsString();
            String replacement = TYPE_RENAMES.get(name);
            if (replacement != null) {
                importDeclaration.setName(replacement);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteTypes(CompilationUnit cu) {
        boolean changed = false;
        for (ObjectCreationExpr expr : cu.findAll(ObjectCreationExpr.class)) {
            changed |= rewriteType(expr.getType());
        }
        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            changed |= rewriteType(type);
        }
        return changed;
    }

    private static boolean rewriteType(ClassOrInterfaceType type) {
        Optional<String> replacement = resolveReplacement(type.getNameWithScope());
        if (replacement.isPresent()) {
            String fqcn = replacement.get();
            type.setName(fqcn.substring(fqcn.lastIndexOf('.') + 1));
            type.setScope(StaticJavaParser.parseClassOrInterfaceType(fqcn.substring(0, fqcn.lastIndexOf('.'))));
            return true;
        }
        return false;
    }

    private static Optional<String> resolveReplacement(String typeName) {
        if (TYPE_RENAMES.containsKey(typeName)) {
            return Optional.of(TYPE_RENAMES.get(typeName));
        }

        for (Map.Entry<String, String> entry : TYPE_RENAMES.entrySet()) {
            String oldFqcn = entry.getKey();
            if (oldFqcn.endsWith('.' + typeName)) {
                return Optional.of(entry.getValue());
            }
        }

        return Optional.empty();
    }
}
