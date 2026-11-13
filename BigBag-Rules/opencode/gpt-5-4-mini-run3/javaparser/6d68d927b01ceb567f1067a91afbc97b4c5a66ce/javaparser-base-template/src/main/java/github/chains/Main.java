package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final String OLD_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String OLD_SIMPLE_NAME = "ScriptResult";
    private static final String OLD_METHOD = "getJavaScriptResult";

    public static void main(final String[] args) throws IOException {
        Path sourceRoot = Paths.get(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        JavaParser parser = new JavaParser(new ParserConfiguration());

        try (java.util.stream.Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(path -> rewriteFile(parser, path));
        }
    }

    private static void rewriteFile(final JavaParser parser, final Path file) {
        try {
            CompilationUnit cu = parser.parse(file).getResult().orElse(null);
            if (cu == null) {
                return;
            }

            LexicalPreservingPrinter.setup(cu);
            boolean[] changed = {false};

            cu.getImports().removeIf(importDeclaration -> {
                boolean match = importDeclaration.getNameAsString().equals(OLD_TYPE);
                changed[0] |= match;
                return match;
            });

            cu.accept(new ModifierVisitor<Void>() {
                @Override
                public Visitable visit(final VariableDeclarator declarator, final Void arg) {
                    Visitable visited = super.visit(declarator, arg);
                    if (!(visited instanceof VariableDeclarator)) {
                        return visited;
                    }

                    VariableDeclarator variable = (VariableDeclarator) visited;
                    if (!isOldType(variable.getType()) || !variable.getInitializer().isPresent()) {
                        return variable;
                    }

                    Expression initializer = variable.getInitializer().get();
                    if (initializer instanceof ObjectCreationExpr) {
                        ObjectCreationExpr creation = (ObjectCreationExpr) initializer;
                        if (isOldType(creation) && creation.getArguments().size() == 1) {
                            variable.setType(new ClassOrInterfaceType(null, "Object"));
                            variable.setInitializer(creation.getArgument(0));
                            changed[0] = true;
                        }
                    }
                    return variable;
                }

                @Override
                public Visitable visit(final MethodCallExpr expr, final Void arg) {
                    Visitable visited = super.visit(expr, arg);
                    if (!(visited instanceof MethodCallExpr)) {
                        return visited;
                    }

                    MethodCallExpr methodCall = (MethodCallExpr) visited;
                    if (!OLD_METHOD.equals(methodCall.getNameAsString()) || methodCall.getArguments().size() != 0) {
                        return methodCall;
                    }

                    if (methodCall.getScope().isPresent()) {
                        Expression scope = methodCall.getScope().get();
                        if (scope instanceof ObjectCreationExpr) {
                            ObjectCreationExpr creation = (ObjectCreationExpr) scope;
                            if (isOldType(creation) && creation.getArguments().size() == 1) {
                                changed[0] = true;
                                return creation.getArgument(0);
                            }
                        }
                    }

                    return methodCall;
                }
            }, null);

            if (changed[0]) {
                Files.write(file, LexicalPreservingPrinter.print(cu).getBytes(StandardCharsets.UTF_8));
            }
        }
        catch (IOException exception) {
            throw new IllegalStateException("Failed to rewrite " + file, exception);
        }
    }

    private static boolean isOldType(final Type type) {
        return type.isClassOrInterfaceType() && isOldType(type.asClassOrInterfaceType());
    }

    private static boolean isOldType(final ClassOrInterfaceType type) {
        return OLD_SIMPLE_NAME.equals(type.getNameAsString()) || OLD_TYPE.equals(type.getNameWithScope());
    }

    private static boolean isOldType(final ObjectCreationExpr expr) {
        return isOldType(expr.getType());
    }
}
