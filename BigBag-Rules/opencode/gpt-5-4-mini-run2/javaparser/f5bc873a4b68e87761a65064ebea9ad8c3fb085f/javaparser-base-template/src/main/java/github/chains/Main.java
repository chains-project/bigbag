package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

public class Main {

    private static final String OLD_TYPE = "org.apache.maven.archiver.ManifestConfiguration";
    private static final String NEW_TYPE = "org.apache.maven.archiver.MavenArchiveConfiguration";
    private static final String TARGET_METHOD = "createArchive";
    private static final String HELPER_METHOD = "toMavenArchiveConfiguration";

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: Main <source-root>");
            return;
        }

        StaticJavaParser.setConfiguration(new ParserConfiguration());
        Path root = Paths.get(args[0]).toAbsolutePath().normalize();

        try {
            Files.walk(root)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void rewriteFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;
            for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
                if (!TARGET_METHOD.equals(call.getNameAsString()) || call.getArguments().size() != 3) {
                    continue;
                }

                Expression oldArgument = call.getArgument(2);
                if (!looksLikeOldManifestType(oldArgument)) {
                    continue;
                }

                ClassOrInterfaceDeclaration owner = call.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null);
                if (owner == null) {
                    continue;
                }

                ensureHelperMethod(owner);
                call.setArgument(2, new MethodCallExpr(null, HELPER_METHOD, NodeList.nodeList(oldArgument.clone())));
                changed = true;
            }

            if (changed) {
                Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + path, e);
        }
    }

    private static boolean looksLikeOldManifestType(Expression expression) {
        if (expression.isObjectCreationExpr()) {
            return expression.asObjectCreationExpr().getType().asString().endsWith("ManifestConfiguration");
        }
        String text = expression.toString();
        return text.contains("ManifestConfiguration");
    }

    private static void ensureHelperMethod(ClassOrInterfaceDeclaration owner) {
        for (MethodDeclaration method : owner.getMethodsByName(HELPER_METHOD)) {
            if (method.getParameters().size() == 1
                && method.getParameter(0).getType().asString().endsWith("ManifestConfiguration")) {
                return;
            }
        }

        MethodDeclaration helper = owner.addMethod(HELPER_METHOD,
            com.github.javaparser.ast.Modifier.Keyword.PRIVATE,
            com.github.javaparser.ast.Modifier.Keyword.STATIC);
        helper.setType(new ClassOrInterfaceType(null, NEW_TYPE));
        helper.addParameter(new Parameter(new ClassOrInterfaceType(null, OLD_TYPE), "manifestConfiguration"));
        helper.setBody(StaticJavaParser.parseBlock("{" +
            "  " + NEW_TYPE + " archiveConfiguration = new " + NEW_TYPE + "();" +
            "  archiveConfiguration.setManifest(manifestConfiguration);" +
            "  return archiveConfiguration;" +
            "}"));
    }
}
