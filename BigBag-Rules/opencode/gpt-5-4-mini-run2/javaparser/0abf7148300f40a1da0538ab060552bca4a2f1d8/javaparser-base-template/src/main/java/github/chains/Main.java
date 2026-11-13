package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Main {

    private static final String TARGET_OWNER = "net.sf.jasperreports.engine.JRPen";
    private static final String TARGET_METHOD = "setLineWidth";
    private static final String TARGET_PARAMETER = "java.lang.Float";

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: Main <source-root>");
            System.exit(1);
        }

        final Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source root does not exist: " + sourceRoot);
        }

        final ParserConfiguration configuration = new ParserConfiguration();
        final CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(sourceRoot));
        configuration.setSymbolResolver(new JavaSymbolSolver(typeSolver));
        StaticJavaParser.setConfiguration(configuration);

        final List<Path> javaFiles = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .forEach(javaFiles::add);
        }
        catch (IOException e) {
            throw new IllegalStateException("Failed to scan source tree", e);
        }

        int changedFiles = 0;
        for (Path file : javaFiles) {
            changedFiles += transformFile(file);
        }

        System.out.println("Updated " + changedFiles + " file(s) for " + TARGET_OWNER + '#' + TARGET_METHOD + "(" + TARGET_PARAMETER + ").");
    }

    private static int transformFile(Path file) {
        try {
            final CompilationUnit compilationUnit = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(compilationUnit);

            boolean changed = false;
            for (MethodCallExpr call : compilationUnit.findAll(MethodCallExpr.class)) {
                changed |= transformCall(call);
            }

            if (!changed) {
                return 0;
            }

            Files.writeString(file, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
            return 1;
        }
        catch (IOException e) {
            throw new IllegalStateException("Failed to rewrite " + file, e);
        }
    }

    private static boolean transformCall(MethodCallExpr call) {
        if (!TARGET_METHOD.equals(call.getNameAsString()) || call.getArguments().size() != 1) {
            return false;
        }

        final Expression argument = call.getArgument(0);
        if (!needsFloatBoxing(argument)) {
            return false;
        }

        final Expression replacement = new MethodCallExpr(
                new NameExpr("Float"),
                "valueOf",
                NodeList.nodeList(new CastExpr(PrimitiveType.floatType(), argument.clone())));
        call.setArgument(0, replacement);
        return true;
    }

    private static boolean needsFloatBoxing(Expression expression) {
        if (expression.isMethodCallExpr()) {
            final MethodCallExpr methodCall = expression.asMethodCallExpr();
            if ("valueOf".equals(methodCall.getNameAsString())
                    && methodCall.getScope().map(scope -> "Float".equals(scope.toString())).orElse(false)) {
                return false;
            }
        }

        try {
            final ResolvedType resolvedType = expression.calculateResolvedType();
            final String typeName = resolvedType.describe();
            return isIntegralType(typeName);
        }
        catch (RuntimeException ex) {
            return isIntegralLiteral(expression);
        }
    }

    private static boolean isIntegralType(String typeName) {
        return "byte".equals(typeName)
                || "short".equals(typeName)
                || "char".equals(typeName)
                || "int".equals(typeName)
                || "long".equals(typeName)
                || "java.lang.Byte".equals(typeName)
                || "java.lang.Short".equals(typeName)
                || "java.lang.Character".equals(typeName)
                || "java.lang.Integer".equals(typeName)
                || "java.lang.Long".equals(typeName);
    }

    private static boolean isIntegralLiteral(Expression expression) {
        if (expression.isIntegerLiteralExpr() || expression.isLongLiteralExpr()) {
            return true;
        }
        if (expression.isUnaryExpr()) {
            final UnaryExpr unaryExpr = expression.asUnaryExpr();
            return unaryExpr.getOperator() == UnaryExpr.Operator.MINUS && isIntegralLiteral(unaryExpr.getExpression());
        }
        if (expression.isEnclosedExpr()) {
            return isIntegralLiteral(expression.asEnclosedExpr().getInner());
        }
        return false;
    }
}
