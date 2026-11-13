package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        JavaParser parser = new JavaParser(new ParserConfiguration());

        try (var files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> transformFile(parser, path));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void transformFile(JavaParser parser, Path path) {
        try {
            parser.parse(path).getResult().ifPresent(cu -> {
                LexicalPreservingPrinter.setup(cu);
                cu.getImports().removeIf(Main::isRemovedImport);
                if (needsStringUtilsImport(cu)) {
                    ensureWildcardStringUtilsImport(cu);
                }
                new Rule().visit(cu, null);
                try {
                    Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static boolean isRemovedImport(ImportDeclaration importDecl) {
        return importDecl.isStatic() && "org.apache.commons.lang3.StringUtils.isAllBlank".equals(importDecl.getNameAsString());
    }

    private static boolean needsStringUtilsImport(CompilationUnit cu) {
        return cu.findAll(MethodCallExpr.class).stream().anyMatch(Main::isBareStringUtilsCall)
                || cu.findAll(com.github.javaparser.ast.expr.NameExpr.class).stream()
                .anyMatch(name -> "EMPTY".equals(name.getNameAsString()));
    }

    private static boolean isBareStringUtilsCall(MethodCallExpr call) {
        if (call.getScope().isPresent()) {
            return false;
        }
        String name = call.getNameAsString();
        return "isAllBlank".equals(name) || "isBlank".equals(name) || "isNotBlank".equals(name)
                || "isNoneBlank".equals(name) || "isNotEmpty".equals(name) || "isEmpty".equals(name)
                || "defaultIfBlank".equals(name) || "removeEnd".equals(name) || "removeStartIgnoreCase".equals(name)
                || "startsWithIgnoreCase".equals(name) || "endsWith".equals(name) || "join".equals(name);
    }

    private static void ensureWildcardStringUtilsImport(CompilationUnit cu) {
        boolean present = cu.getImports().stream().anyMatch(importDecl -> importDecl.isStatic()
                && importDecl.isAsterisk()
                && "org.apache.commons.lang3.StringUtils".equals(importDecl.getNameAsString()));
        if (!present) {
            cu.addImport("org.apache.commons.lang3.StringUtils", true, true);
        }
    }

    private static final class Rule extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            Visitable visited = super.visit(n, arg);
            if (!(visited instanceof MethodCallExpr)) {
                return visited;
            }

            MethodCallExpr call = (MethodCallExpr) visited;
            if (isStringUtilsIsAllBlank(call)) {
                return rewriteIsAllBlank(call);
            }
            return call;
        }

        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            Visitable visited = super.visit(n, arg);
            if (!(visited instanceof ObjectCreationExpr)) {
                return visited;
            }

            ObjectCreationExpr creation = (ObjectCreationExpr) visited;
            if (isOldClientHelperCtor(creation)) {
                return rewriteClientHelperCtor(creation);
            }
            return creation;
        }

        private boolean isStringUtilsIsAllBlank(MethodCallExpr call) {
            return "isAllBlank".equals(call.getNameAsString()) && call.getArguments().size() > 1;
        }

        private Expression rewriteIsAllBlank(MethodCallExpr call) {
            Expression rewritten = null;
            for (Expression arg : call.getArguments()) {
                MethodCallExpr blankCall = new MethodCallExpr(
                        new com.github.javaparser.ast.expr.NameExpr("org.apache.commons.lang3.StringUtils"),
                        "isBlank");
                blankCall.addArgument(arg.clone());
                rewritten = rewritten == null ? blankCall : new BinaryExpr(rewritten, blankCall, BinaryExpr.Operator.AND);
            }
            return rewritten == null ? new com.github.javaparser.ast.expr.BooleanLiteralExpr(true) : rewritten;
        }

        private boolean isOldClientHelperCtor(ObjectCreationExpr creation) {
            return creation.getType().getNameAsString().equals("ClientHelper") && creation.getArguments().size() == 4;
        }

        private Expression rewriteClientHelperCtor(ObjectCreationExpr creation) {
            Expression credentialsId = creation.getArgument(0).clone();
            Expression listener = creation.getArgument(1).clone();
            Expression clientName = creation.getArgument(2).clone();
            Expression charset = creation.getArgument(3).clone();

            String rewritten = "new org.jenkinsci.plugins.p4.client.ClientHelper(" +
                    "org.jenkinsci.plugins.p4.client.ConnectionHelper.findCredential(" + credentialsId + "), " +
                    listener + ", new org.jenkinsci.plugins.p4.workspace.StaticWorkspaceImpl(" +
                    charset + ", false, " + clientName + "))";
            return StaticJavaParser.parseExpression(rewritten);
        }
    }
}
