package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_SCRIPT_RESULT_FQCN = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String NEW_SCRIPT_RESULT_FQCN = "org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult";

    public static void main(String[] args) throws IOException {
        Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        JavaParser parser = new JavaParser(new ParserConfiguration());

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(path -> transform(parser, path));
        }
    }

    private static void transform(JavaParser parser, Path path) {
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            CompilationUnit cu = parser.parse(source).getResult().orElse(null);
            if (cu == null) {
                throw new IllegalStateException("Could not parse " + path);
            }

            LexicalPreservingPrinter.setup(cu);
            boolean[] changed = {false};
            cu.accept(new ModifierVisitor<Boolean>() {
                private boolean isOldScriptResultType(ClassOrInterfaceType type) {
                    String text = type.asString();
                    return OLD_SCRIPT_RESULT_FQCN.equals(text)
                            || text.endsWith("." + OLD_SCRIPT_RESULT_FQCN)
                            || text.equals("ScriptResult");
                }

                private boolean isOldScriptResultCreation(ObjectCreationExpr creationExpr) {
                    String text = creationExpr.getType().asString();
                    return OLD_SCRIPT_RESULT_FQCN.equals(text)
                            || text.endsWith("." + OLD_SCRIPT_RESULT_FQCN)
                            || text.equals("ScriptResult");
                }

                @Override
                public Visitable visit(ClassOrInterfaceType type, Boolean unused) {
                    if (isOldScriptResultType(type)) {
                        changed[0] = true;
                        return new ClassOrInterfaceType(null, "Object");
                    }
                    return super.visit(type, unused);
                }

                @Override
                public Visitable visit(ObjectCreationExpr expr, Boolean unused) {
                    if (isOldScriptResultCreation(expr) && !expr.getArguments().isEmpty()) {
                        changed[0] = true;
                        return expr.getArgument(0);
                    }
                    return super.visit(expr, unused);
                }

                @Override
                public Visitable visit(MethodCallExpr expr, Boolean unused) {
                    if ("getJavaScriptResult".equals(expr.getNameAsString()) && expr.getScope().isPresent()) {
                        changed[0] = true;
                        return expr.getScope().get();
                    }
                    return super.visit(expr, unused);
                }

                @Override
                public Visitable visit(VariableDeclarator variableDeclarator, Boolean unused) {
                    Visitable visited = super.visit(variableDeclarator, unused);
                    if (visited instanceof VariableDeclarator) {
                        VariableDeclarator vd = (VariableDeclarator) visited;
                        if (vd.getType().isClassOrInterfaceType() && isOldScriptResultType(vd.getType().asClassOrInterfaceType())) {
                            changed[0] = true;
                            vd.setType(new ClassOrInterfaceType(null, "Object"));
                        }
                        vd.getInitializer().ifPresent(initializer -> {
                            if (initializer.isObjectCreationExpr() && isOldScriptResultCreation(initializer.asObjectCreationExpr())) {
                                changed[0] = true;
                                vd.setInitializer(initializer.asObjectCreationExpr().getArgument(0));
                            }
                        });
                    }
                    return visited;
                }

                @Override
                public Visitable visit(VariableDeclarationExpr expr, Boolean unused) {
                    Visitable visited = super.visit(expr, unused);
                    if (visited instanceof VariableDeclarationExpr) {
                        VariableDeclarationExpr declarationExpr = (VariableDeclarationExpr) visited;
                        declarationExpr.getVariables().forEach(variableDeclarator -> {
                            if (variableDeclarator.getType().isClassOrInterfaceType()
                                    && isOldScriptResultType(variableDeclarator.getType().asClassOrInterfaceType())) {
                                changed[0] = true;
                                variableDeclarator.setType(new ClassOrInterfaceType(null, "Object"));
                            }
                        });
                    }
                    return visited;
                }
            }, Boolean.FALSE);

            cu.getImports().removeIf(importDecl -> importDecl.getNameAsString().equals(OLD_SCRIPT_RESULT_FQCN));
            cu.getImports().removeIf(importDecl -> importDecl.getNameAsString().equals(NEW_SCRIPT_RESULT_FQCN));

            if (changed[0]) {
                Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (Exception ex) {
            throw new RuntimeException("Failed to transform " + path, ex);
        }
    }
}
