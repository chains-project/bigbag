package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String TARGET_METHOD = "setLineWidth";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java github.chains.Main <source-root>");
            System.exit(1);
        }

        Path sourceRoot = Path.of(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Not a directory: " + sourceRoot);
        }

        ParserConfiguration configuration = new ParserConfiguration();
        configuration.setCharacterEncoding(StandardCharsets.UTF_8);
        JavaParser parser = new JavaParser(configuration);

        List<Path> javaFiles = new ArrayList<>();
        try (var stream = Files.walk(sourceRoot)) {
            stream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(javaFiles::add);
        }

        for (Path javaFile : javaFiles) {
            transformFile(parser, javaFile);
        }
    }

    private static void transformFile(JavaParser parser, Path javaFile) throws IOException {
        CompilationUnit compilationUnit = parser.parse(javaFile).getResult().orElse(null);
        if (compilationUnit == null) {
            throw new IOException("Unable to parse " + javaFile);
        }

        LexicalPreservingPrinter.setup(compilationUnit);
        boolean changed = false;

        for (MethodCallExpr call : compilationUnit.findAll(MethodCallExpr.class)) {
            if (!TARGET_METHOD.equals(call.getNameAsString()) || call.getArguments().size() != 1) {
                continue;
            }
            if (isAlreadyBoxed(call.getArgument(0))) {
                continue;
            }

            call.setArgument(0, boxLineWidth(call.getArgument(0)));
            changed = true;
        }

        if (changed) {
            Files.writeString(javaFile, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
        }
    }

    private static Expression boxLineWidth(Expression expression) {
        MethodCallExpr valueOf = new MethodCallExpr(new NameExpr("Float"), "valueOf");
        valueOf.addArgument(new CastExpr(PrimitiveType.floatType(), expression.clone()));
        return valueOf;
    }

    private static boolean isAlreadyBoxed(Expression expression) {
        if (expression.isMethodCallExpr()) {
            MethodCallExpr methodCallExpr = expression.asMethodCallExpr();
            return methodCallExpr.getScope().map(Node::toString).orElse("").equals("Float")
                    && "valueOf".equals(methodCallExpr.getNameAsString());
        }
        return expression.isCastExpr() && expression.asCastExpr().getType().asString().equals("Float");
    }
}
