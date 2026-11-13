package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class Main {
    private static final Set<String> TARGET_METHODS = new HashSet<>();

    static {
        TARGET_METHODS.add("setFuture");
        TARGET_METHODS.add("setCompatibleFuture");
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: Main <source-root> [<source-root>...]");
            System.exit(1);
        }

        ParserConfiguration configuration = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11);
        JavaParser parser = new JavaParser(configuration);

        int changed = 0;
        for (String arg : args) {
            Path root = Paths.get(arg);
            if (!Files.exists(root)) {
                continue;
            }
            changed += transformRoot(parser, root);
        }

        System.out.println("Updated files: " + changed);
    }

    private static int transformRoot(JavaParser parser, Path root) {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(files::add);
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk " + root, e);
        }

        int changed = 0;
        for (Path file : files) {
            try {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                CompilationUnit cu = parser.parse(source).getResult().orElse(null);
                if (cu == null) {
                    continue;
                }
                LexicalPreservingPrinter.setup(cu);
                boolean updated = transformCompilationUnit(cu);
                if (updated) {
                    Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
                    changed++;
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to transform " + file, e);
            }
        }
        return changed;
    }

    private static boolean transformCompilationUnit(CompilationUnit cu) {
        boolean[] changed = {false};
        cu.findAll(MethodCallExpr.class).forEach(call -> {
            if (!TARGET_METHODS.contains(call.getNameAsString()) || call.getArguments().size() != 1) {
                return;
            }
            Map<String, String> variableTypes = collectVariableTypes(call);
            Expression argument = call.getArgument(0);
            Expression replacement = rewriteArgument(argument, variableTypes);
            if (replacement != null && !replacement.toString().equals(argument.toString())) {
                call.setArgument(0, replacement);
                changed[0] = true;
            }
        });
        return changed[0];
    }

    private static Map<String, String> collectVariableTypes(MethodCallExpr call) {
        Map<String, String> variableTypes = new HashMap<>();
        call.findAncestor(MethodDeclaration.class).ifPresent(method -> {
            method.findAll(VariableDeclarator.class).forEach(vd -> {
                if (vd.findAncestor(MethodCallExpr.class).isPresent()) {
                    return;
                }
                variableTypes.put(vd.getNameAsString(), vd.getType().toString());
            });
        });
        call.findAncestor(BlockStmt.class).ifPresent(block -> {
            for (com.github.javaparser.ast.stmt.Statement statement : block.getStatements()) {
                if (statement.getBegin().isPresent() && call.getBegin().isPresent()
                        && statement.getBegin().get().isAfter(call.getBegin().get())) {
                    break;
                }
                statement.findAll(VariableDeclarator.class).forEach(vd ->
                        variableTypes.put(vd.getNameAsString(), vd.getType().toString()));
            }
        });
        return variableTypes;
    }

    private static Expression rewriteArgument(Expression expression, Map<String, String> variableTypes) {
        if (isAsyncRpcResultExpression(expression, variableTypes)) {
            return parseExpression(expression.toString() + ".getResponseFuture()");
        }
        if (expression instanceof EnclosedExpr) {
            Expression inner = rewriteArgument(((EnclosedExpr) expression).getInner(), variableTypes);
            return inner == null ? null : new EnclosedExpr(inner);
        }
        if (expression instanceof CastExpr) {
            return rewriteArgument(((CastExpr) expression).getExpression(), variableTypes);
        }
        if (expression instanceof ConditionalExpr) {
            ConditionalExpr conditional = (ConditionalExpr) expression;
            Expression thenExpr = rewriteArgument(conditional.getThenExpr(), variableTypes);
            Expression elseExpr = rewriteArgument(conditional.getElseExpr(), variableTypes);
            if (thenExpr == null && elseExpr == null) {
                return null;
            }
            return new ConditionalExpr(
                    conditional.getCondition(),
                    thenExpr == null ? conditional.getThenExpr() : thenExpr,
                    elseExpr == null ? conditional.getElseExpr() : elseExpr);
        }
        return null;
    }

    private static boolean isAsyncRpcResultExpression(Expression expression, Map<String, String> variableTypes) {
        if (expression instanceof NameExpr) {
            String name = ((NameExpr) expression).getNameAsString();
            String type = variableTypes.get(name);
            return type != null && type.endsWith("AsyncRpcResult");
        }
        if (expression instanceof ObjectCreationExpr) {
            return ((ObjectCreationExpr) expression).getType().getNameAsString().equals("AsyncRpcResult");
        }
        if (expression instanceof MethodCallExpr) {
            MethodCallExpr call = (MethodCallExpr) expression;
            if (call.getNameAsString().equals("newDefaultAsyncResult")) {
                return call.getScope().map(scope -> scope.toString().endsWith("AsyncRpcResult")).orElse(false);
            }
        }
        return expression.toString().contains("AsyncRpcResult");
    }

    private static Expression parseExpression(String source) {
        return new JavaParser().parseExpression(source).getResult().orElseThrow(() ->
                new IllegalArgumentException("Unable to parse expression: " + source));
    }
}
