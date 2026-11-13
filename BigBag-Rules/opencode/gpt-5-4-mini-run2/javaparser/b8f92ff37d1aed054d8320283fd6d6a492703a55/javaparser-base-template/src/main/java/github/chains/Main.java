package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithArguments;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".");

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void transformFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            LexicalPreservingPrinter.setup(cu);

            boolean[] changed = new boolean[] { false };

            for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
                if (isOldWildcardFactoryCall(call)) {
                    ObjectCreationExpr replacement = new ObjectCreationExpr(
                            null,
                            StaticJavaParser.parseClassOrInterfaceType("org.apache.maven.surefire.api.testset.TestListResolver"),
                            NodeList.nodeList(new StringLiteralExpr("*.class")));
                    call.replace(replacement);
                    changed[0] = true;
                }
            }

            if (changed[0]) {
                Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static boolean isOldWildcardFactoryCall(MethodCallExpr call) {
        return call.getNameAsString().equals("getWildcard")
                && call.getArguments().isEmpty()
                && call.getScope().map(Expression::toString)
                .map(scope -> scope.equals("TestListResolver")
                        || scope.equals("org.apache.maven.surefire.api.testset.TestListResolver")
                        || scope.endsWith(".TestListResolver"))
                .orElse(true);
    }
}
