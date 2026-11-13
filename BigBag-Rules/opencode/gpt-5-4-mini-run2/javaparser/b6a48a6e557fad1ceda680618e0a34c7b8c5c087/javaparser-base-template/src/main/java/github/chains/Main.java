package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final String OLD_FLYWAY_TYPE = "org.flywaydb.core.Flyway";
    private static final String NEW_FLYWAY_TYPE = "org.flywaydb.core.api.configuration.FluentConfiguration";

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        final Path sourceRoot = Paths.get(args[0]);
        final JavaParser parser = new JavaParser(new ParserConfiguration());

        Files.walk(sourceRoot)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        final CompilationUnit cu = parser.parse(path).getResult()
                                .orElseThrow(() -> new IllegalStateException("Parse failed: " + path));
                        final boolean[] changed = {false};

                        cu.accept(new VoidVisitorAdapter<Void>() {
                            @Override
                            public void visit(FieldDeclaration n, Void arg) {
                                super.visit(n, arg);
                                if (matchesFlywayType(n.getElementType())) {
                                    n.setAllTypes(parseType(NEW_FLYWAY_TYPE));
                                    changed[0] = true;
                                }
                            }

                            @Override
                            public void visit(VariableDeclarator n, Void arg) {
                                super.visit(n, arg);
                                if (matchesFlywayType(n.getType())) {
                                    n.setType(parseType(NEW_FLYWAY_TYPE));
                                    changed[0] = true;
                                }
                            }

                            @Override
                            public void visit(ObjectCreationExpr n, Void arg) {
                                super.visit(n, arg);
                                if (matchesFlywayType(n.getType())) {
                                    n.setType(StaticJavaParser.parseClassOrInterfaceType(NEW_FLYWAY_TYPE));
                                    changed[0] = true;
                                }
                            }

                            @Override
                            public void visit(MethodCallExpr n, Void arg) {
                                super.visit(n, arg);
                                switch (n.getNameAsString()) {
                                    case "setDataSource":
                                        n.setName("dataSource");
                                        changed[0] = true;
                                        break;
                                    case "setLocations":
                                        n.setName("locations");
                                        changed[0] = true;
                                        break;
                                    case "setClassLoader":
                                        n.setName("classLoader");
                                        changed[0] = true;
                                        break;
                                    case "setValidateOnMigrate":
                                        n.setName("validateOnMigrate");
                                        changed[0] = true;
                                        break;
                                    default:
                                        break;
                                }
                            }

                            @Override
                            public void visit(MethodDeclaration n, Void arg) {
                                super.visit(n, arg);
                                n.findAll(VariableDeclarator.class).forEach(variable -> {
                                    if (variable.getInitializer().isEmpty() || !variable.getInitializer().get().isObjectCreationExpr()) {
                                        return;
                                    }

                                    final String variableName = variable.getNameAsString();
                                    final ObjectCreationExpr creation = variable.getInitializer().get().asObjectCreationExpr();
                                    boolean isFlywayBuilder = matchesFlywayType(variable.getType()) || isBuilderType(variable.getType()) || matchesFlywayType(creation.getType());
                                    if (!isFlywayBuilder) {
                                        return;
                                    }

                                    n.findAll(MethodCallExpr.class).forEach(call -> {
                                        if (!call.hasScope() || !call.getScope().get().isNameExpr()) {
                                            return;
                                        }
                                        if (!variableName.equals(call.getScope().get().asNameExpr().getNameAsString())) {
                                            return;
                                        }

                                        if ("classLoader".equals(call.getNameAsString())) {
                                            if (!call.getArguments().isEmpty()) {
                                                final Expression classLoaderArg = call.getArgument(0).clone();
                                                creation.setType(StaticJavaParser.parseClassOrInterfaceType(NEW_FLYWAY_TYPE));
                                                creation.getArguments().clear();
                                                creation.getArguments().add(classLoaderArg);
                                                changed[0] = true;
                                            }
                                            call.findAncestor(ExpressionStmt.class).ifPresent(ExpressionStmt::remove);
                                        }
                                    });

                                    n.findAll(com.github.javaparser.ast.stmt.ReturnStmt.class).forEach(returnStmt -> {
                                        if (!returnStmt.getExpression().isPresent()) {
                                            return;
                                        }
                                        final Expression expression = returnStmt.getExpression().get();
                                        if (expression.isNameExpr() && variableName.equals(expression.asNameExpr().getNameAsString())
                                                && matchesFlywayType(n.getType())) {
                                            returnStmt.setExpression(StaticJavaParser.parseExpression(variableName + ".load()"));
                                            changed[0] = true;
                                        }
                                    });
                                });
                            }

                            private boolean matchesFlywayType(Type type) {
                                return OLD_FLYWAY_TYPE.equals(type.asString()) || "Flyway".equals(type.asString());
                            }

                            private boolean isBuilderType(Type type) {
                                return NEW_FLYWAY_TYPE.equals(type.asString()) || "FluentConfiguration".equals(type.asString());
                            }

                        }, null);

                        if (changed[0]) {
                            Files.write(path, cu.toString().getBytes(StandardCharsets.UTF_8));
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private static Type parseType(String type) {
        return StaticJavaParser.parseType(type);
    }
}
