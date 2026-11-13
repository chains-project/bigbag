package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.util.concurrent.atomic.AtomicBoolean;

public class Main {

    private static final String TARGET_FQCN = "org.tinspin.index.kdtree.KDTree";
    private static final String TARGET_SIMPLE_NAME = "KDTree";
    private static final String TARGET_METHOD = "create";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected source root path as the first argument");
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Not a directory: " + sourceRoot);
        }

        ParserConfiguration configuration = new ParserConfiguration();
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        configuration.setSymbolResolver(new JavaSymbolSolver(typeSolver));

        JavaParser parser = new JavaParser(configuration);

        try {
            Files.walk(sourceRoot)
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(path -> transformFile(parser, path));
        } catch (IOException e) {
            throw new RuntimeException("Failed while walking source tree " + sourceRoot, e);
        }
    }

    private static void transformFile(JavaParser parser, Path file) {
        try {
            ParseResult<CompilationUnit> result = parser.parse(file);
            Optional<CompilationUnit> parsed = result.getResult();
            if (!parsed.isPresent()) {
                return;
            }

            CompilationUnit cu = parsed.get();
            LexicalPreservingPrinter.setup(cu);

            AtomicBoolean changed = new AtomicBoolean(false);
            cu.accept(new ModifierVisitor<Void>() {
                @Override
                public Visitable visit(MethodCallExpr call, Void arg) {
                    super.visit(call, arg);
                    if (shouldRewrite(call, cu)) {
                        NodeList<Expression> arguments = call.getArguments();
                        if (arguments.size() == 2) {
                            arguments.remove(1);
                            changed.set(true);
                        }
                    }
                    return call;
                }
            }, null);

            if (changed.get()) {
                Files.write(file, LexicalPreservingPrinter.print(cu).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static boolean shouldRewrite(MethodCallExpr call, CompilationUnit cu) {
        if (!TARGET_METHOD.equals(call.getNameAsString()) || call.getArguments().size() != 2) {
            return false;
        }

        if (!call.getScope().isPresent()) {
            return false;
        }

        Expression scope = call.getScope().get();
        if (isTargetScopeText(scope)) {
            return true;
        }

        try {
            ResolvedMethodDeclaration resolved = call.resolve();
            if (TARGET_FQCN.equals(resolved.declaringType().getQualifiedName())
                    && TARGET_METHOD.equals(resolved.getName())
                    && resolved.getNumberOfParams() == 2) {
                return true;
            }
        } catch (RuntimeException ex) {
            // fall through to syntactic matching
        }

        return importsTargetType(cu) && scopeIsSimpleTarget(scope);
    }

    private static boolean isTargetScopeText(Expression scope) {
        String text = scope.toString();
        return TARGET_SIMPLE_NAME.equals(text) || TARGET_FQCN.equals(text);
    }

    private static boolean scopeIsSimpleTarget(Expression scope) {
        if (scope instanceof NameExpr) {
            return TARGET_SIMPLE_NAME.equals(((NameExpr) scope).getNameAsString());
        }
        return TARGET_SIMPLE_NAME.equals(scope.toString());
    }

    private static boolean importsTargetType(CompilationUnit cu) {
        return cu.getImports().stream().anyMatch(importDecl -> importDecl.getNameAsString().equals(TARGET_FQCN)
                || (importDecl.isAsterisk() && importDecl.getNameAsString().equals("org.tinspin.index.kdtree")));
    }
}
