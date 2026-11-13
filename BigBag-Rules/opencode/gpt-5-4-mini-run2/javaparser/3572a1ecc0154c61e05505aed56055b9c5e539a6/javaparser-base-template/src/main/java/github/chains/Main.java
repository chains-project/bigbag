package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.Type;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    private static final String OLD_FQCN = "org.apache.thrift.transport.TFastFramedTransport";
    private static final String NEW_FQCN = "org.apache.thrift.transport.layered.TFastFramedTransport";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source root path");
        }

        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        List<Path> files = new ArrayList<>();
        Files.walk(root)
                .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                .forEach(files::add);

        for (Path file : files) {
            rewrite(file);
        }
    }

    private static void rewrite(Path file) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(file);
        boolean changed = false;

        for (ImportDeclaration importDeclaration : cu.getImports()) {
            if (!importDeclaration.isAsterisk() && OLD_FQCN.equals(importDeclaration.getNameAsString())) {
                importDeclaration.setName(NEW_FQCN);
                changed = true;
            }
        }

        for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
            if (!matchesOldTransport(creation)) {
                continue;
            }

            ClassOrInterfaceDeclaration owner = creation.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null);
            if (owner == null) {
                continue;
            }

            if (creation.getArguments().size() < 1 || creation.getArguments().size() > 3) {
                continue;
            }

            String helperName = helperName(creation.getArguments().size());
            if (owner.getMethodsByName(helperName).isEmpty()) {
                owner.addMember(buildHelper(helperName, creation.getArguments().size()));
            }

            String args = creation.getArguments().stream().map(Expression::toString).collect(Collectors.joining(", "));
            creation.replace(StaticJavaParser.parseExpression(helperName + "(" + args + ")"));
            changed = true;
        }

        for (Type type : cu.findAll(Type.class)) {
            if (type.isClassOrInterfaceType()) {
                String name = type.asClassOrInterfaceType().getNameAsString();
                if (OLD_FQCN.equals(type.asString()) || "TFastFramedTransport".equals(name)) {
                    type.replace(StaticJavaParser.parseType(NEW_FQCN));
                    changed = true;
                }
            }
        }

        if (changed) {
            Files.writeString(file, cu.toString(), StandardCharsets.UTF_8);
        }
    }

    private static boolean matchesOldTransport(ObjectCreationExpr creation) {
        String typeName = creation.getType().asString();
        return OLD_FQCN.equals(typeName) || "TFastFramedTransport".equals(creation.getType().getNameAsString());
    }

    private static String helperName(int arity) {
        return "createTFastFramedTransport" + arity;
    }

    private static MethodDeclaration buildHelper(String helperName, int arity) {
        MethodDeclaration method = new MethodDeclaration();
        method.addModifier(Modifier.Keyword.PRIVATE, Modifier.Keyword.STATIC);
        method.setName(helperName);
        method.setType(StaticJavaParser.parseType(NEW_FQCN));
        for (int i = 0; i < arity; i++) {
            method.addParameter(i == 0 ? StaticJavaParser.parseType("org.apache.thrift.transport.TTransport") : StaticJavaParser.parseType("int"), "arg" + i);
        }
        StringBuilder args = new StringBuilder();
        for (int i = 0; i < arity; i++) {
            if (i > 0) {
                args.append(", ");
            }
            args.append("arg").append(i);
        }
        BlockStmt body = StaticJavaParser.parseBlock("{ try { return new " + NEW_FQCN + "(" + args + "); } catch (org.apache.thrift.transport.TTransportException e) { throw new RuntimeException(e); } }");
        method.setBody(body);
        return method;
    }
}
