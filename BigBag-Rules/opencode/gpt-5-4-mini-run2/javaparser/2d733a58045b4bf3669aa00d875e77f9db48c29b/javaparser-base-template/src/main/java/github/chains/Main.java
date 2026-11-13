package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_FLYWAY = "org.flywaydb.core.Flyway";
    private static final String NEW_CONFIG = "org.flywaydb.core.api.configuration.FluentConfiguration";
    private static final String CONFIGURE_CALL = "org.flywaydb.core.Flyway.configure()";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one source root path");
        }

        final Path root = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::transform);
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source root " + root, e);
        }
    }

    private static void transform(Path file) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);

            final Set<String> fluentVariables = new HashSet<>();

            for (VariableDeclarator variable : cu.findAll(VariableDeclarator.class)) {
                if (variable.getInitializer().isPresent() && isFlywayInitializer(variable.getInitializer().get())) {
                    variable.setType(parseType(NEW_CONFIG));
                    variable.setInitializer(StaticJavaParser.parseExpression(CONFIGURE_CALL));
                    fluentVariables.add(variable.getNameAsString());
                }
            }

            for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
                if (call.getScope().isPresent() && call.getScope().get().isNameExpr()) {
                    final String scope = call.getScope().get().asNameExpr().getNameAsString();
                    if (!fluentVariables.contains(scope)) {
                        continue;
                    }
                    if (call.getNameAsString().equals("classLoader") && call.getArguments().size() == 1) {
                        final VariableDeclarator variable = findVariable(cu, scope);
                        if (variable != null) {
                            variable.setInitializer(new MethodCallExpr(new NameExpr(OLD_FLYWAY), "configure", call.getArguments()));
                        }
                        call.findAncestor(ExpressionStmt.class).ifPresent(ExpressionStmt::remove);
                        continue;
                    }
                }
                if (call.getNameAsString().equals("setDataSource") && call.getArguments().size() == 1) {
                    call.setName("dataSource");
                } else if (call.getNameAsString().equals("setLocations") && call.getArguments().size() == 1) {
                    call.setName("locations");
                } else if (call.getNameAsString().equals("setValidateOnMigrate") && call.getArguments().size() == 1) {
                    call.setName("validateOnMigrate");
                }
            }

            for (ReturnStmt returnStmt : cu.findAll(ReturnStmt.class)) {
                final Expression expression = returnStmt.getExpression().orElse(null);
                if (expression != null && expression.isNameExpr() && fluentVariables.contains(expression.asNameExpr().getNameAsString())) {
                    returnStmt.setExpression(new MethodCallExpr(new NameExpr(expression.asNameExpr().getNameAsString()), "load"));
                }
            }

            if (!fluentVariables.isEmpty()) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static boolean isFlywayInitializer(Expression expression) {
        return expression.isObjectCreationExpr() && isFlywayType(expression.asObjectCreationExpr())
                || expression.isMethodCallExpr() && isConfigureCall(expression.asMethodCallExpr());
    }

    private static boolean isFlywayType(ObjectCreationExpr creationExpr) {
        return isFlywayType(creationExpr.getType().toString());
    }

    private static boolean isFlywayType(String typeName) {
        return OLD_FLYWAY.equals(typeName) || typeName.endsWith(".Flyway") || "Flyway".equals(typeName);
    }

    private static boolean isConfigureCall(MethodCallExpr call) {
        return "configure".equals(call.getNameAsString())
                && call.getScope().isPresent()
                && OLD_FLYWAY.equals(call.getScope().get().toString());
    }

    private static ClassOrInterfaceType parseType(String typeName) {
        return StaticJavaParser.parseClassOrInterfaceType(typeName);
    }

    private static VariableDeclarator findVariable(CompilationUnit cu, String name) {
        return cu.findAll(VariableDeclarator.class).stream()
                .filter(variable -> variable.getNameAsString().equals(name))
                .findFirst()
                .orElse(null);
    }

}
