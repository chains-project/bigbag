package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        try {
            List<Path> javaFiles = Files.walk(root)
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            for (Path file : javaFiles) {
                transformFile(file);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform sources", e);
        }
    }

    private static void transformFile(Path file) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(file);
        boolean changed = false;

        for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
            Expression replacement = transformLegacyClientHelper(creation);
            if (replacement != null) {
                creation.replace(replacement);
                changed = true;
            }

            if (isLegacyConnectionHelper(creation)) {
                changed = addIOExceptionToEnclosingMethod(creation) || changed;
            }
        }

        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            Expression replacement = transformIsAllBlank(call, cu);
            if (replacement != null) {
                call.replace(replacement);
                changed = true;
            }
        }

        boolean importChanged = cu.getImports().removeIf(importDecl -> importDecl.isStatic()
                && importDecl.toString().contains("StringUtils.isAllBlank"));

        if (changed || importChanged) {
            Files.writeString(file, cu.toString());
        }
    }

    private static Expression transformLegacyClientHelper(ObjectCreationExpr creation) {
        if (!"ClientHelper".equals(creation.getType().getNameAsString()) || creation.getArguments().size() != 4) {
            return null;
        }

        NodeList<Expression> args = creation.getArguments();
        String replacement = String.format(
                "new org.jenkinsci.plugins.p4.client.ClientHelper(" +
                        "org.jenkinsci.plugins.p4.client.ConnectionHelper.findCredential(%s), %s, " +
                        "new org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl(%s, false, %s, " +
                        "new org.jenkinsci.plugins.p4.workspace.WorkspaceSpec(\"\", \"\"), false))",
                args.get(0), args.get(1), args.get(3), args.get(2));
        return StaticJavaParser.parseExpression(replacement);
    }

    private static boolean isLegacyConnectionHelper(ObjectCreationExpr creation) {
        return "ConnectionHelper".equals(creation.getType().getNameAsString()) && creation.getArguments().size() == 2;
    }

    private static boolean addIOExceptionToEnclosingMethod(ObjectCreationExpr creation) {
        return creation.findAncestor(MethodDeclaration.class).map(method -> {
            boolean alreadyDeclared = method.getThrownExceptions().stream()
                    .map(Object::toString)
                    .anyMatch(type -> type.equals("IOException") || type.equals("java.io.IOException"));
            if (alreadyDeclared) {
                return false;
            }
            method.addThrownException(new ClassOrInterfaceType(null, "java.io.IOException"));
            return true;
        }).orElse(false);
    }

    private static Expression transformIsAllBlank(MethodCallExpr call, CompilationUnit cu) {
        if (!"isAllBlank".equals(call.getNameAsString()) || call.getArguments().isEmpty()) {
            return null;
        }

        if (call.getScope().isPresent()) {
            String scope = call.getScope().get().toString();
            if (!scope.endsWith("StringUtils") && !scope.endsWith("org.apache.commons.lang3.StringUtils")) {
                return null;
            }
        } else if (!hasRelevantStringUtilsImport(cu)) {
            return null;
        }

        if (call.getArguments().size() == 1) {
            return call.getArgument(0);
        }

        List<String> checks = new ArrayList<>();
        for (Expression argument : call.getArguments()) {
            checks.add("org.apache.commons.lang3.StringUtils.isBlank(" + argument + ")");
        }

        return StaticJavaParser.parseExpression(String.join(" && ", checks));
    }

    private static boolean hasRelevantStringUtilsImport(CompilationUnit cu) {
        return cu.getImports().stream().anyMatch(importDecl ->
                (importDecl.isStatic()
                        && importDecl.getNameAsString().equals("org.apache.commons.lang3.StringUtils.isAllBlank"))
                        || importDecl.getNameAsString().equals("org.apache.commons.lang3.StringUtils"));
    }

}
