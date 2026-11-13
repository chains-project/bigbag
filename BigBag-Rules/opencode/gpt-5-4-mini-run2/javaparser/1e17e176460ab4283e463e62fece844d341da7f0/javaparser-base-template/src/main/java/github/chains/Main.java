package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.type.ReferenceType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected source root path");
        }

        Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Not a directory: " + root);
        }

        try {
            Files.walk(root)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void transformFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            boolean changed = false;

            changed |= transformObjectCreations(cu);
            changed |= transformConstructors(cu);
            changed |= transformGetPropertiesOverrides(cu);

            if (changed) {
                Files.writeString(path, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static boolean transformObjectCreations(CompilationUnit cu) {
        boolean changed = false;

        for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
            String typeName = creation.getType().getNameAsString();
            int argCount = creation.getArguments().size();

            if ("Representer".equals(typeName) && argCount == 0) {
                NodeList<Expression> args = new NodeList<>();
                args.add(StaticJavaParser.parseExpression("new DumperOptions()"));
                creation.setArguments(args);
                ensureImport(cu, "org.yaml.snakeyaml.DumperOptions");
                changed = true;
                continue;
            }

            if ("Constructor".equals(typeName) && argCount == 1) {
                Expression first = creation.getArgument(0);
                if (!isSimpleTypeCreation(first, "LoaderOptions")) {
                    NodeList<Expression> args = new NodeList<>();
                    args.add(first);
                    args.add(StaticJavaParser.parseExpression("new LoaderOptions()"));
                    creation.setArguments(args);
                    ensureImport(cu, "org.yaml.snakeyaml.LoaderOptions");
                    changed = true;
                }
                continue;
            }

            if ("Yaml".equals(typeName) && argCount == 4) {
                Expression last = creation.getArgument(3);
                if (!isSimpleTypeCreation(last, "LoaderOptions")) {
                    String lastType = last.isObjectCreationExpr()
                            ? last.asObjectCreationExpr().getType().getNameAsString()
                            : last.toString();
                    if (lastType.endsWith("Resolver")) {
                        NodeList<Expression> args = new NodeList<>();
                        args.add(creation.getArgument(0));
                        args.add(creation.getArgument(1));
                        args.add(creation.getArgument(2));
                        args.add(StaticJavaParser.parseExpression("new LoaderOptions()"));
                        args.add(last);
                        creation.setArguments(args);
                        ensureImport(cu, "org.yaml.snakeyaml.LoaderOptions");
                        changed = true;
                    }
                }
            }
        }

        return changed;
    }

    private static boolean transformConstructors(CompilationUnit cu) {
        boolean changed = false;

        for (ClassOrInterfaceDeclaration type : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            boolean extendsRepresenter = extendsSimpleName(type, "Representer") || extendsSimpleName(type, "BaseRepresenter");
            boolean extendsConstructor = extendsSimpleName(type, "Constructor");

            if (!extendsRepresenter && !extendsConstructor) {
                continue;
            }

            for (ConstructorDeclaration ctor : type.getConstructors()) {
                if (extendsRepresenter) {
                    if (!hasExplicitSuperInvocation(ctor)) {
                        ctor.getBody().addStatement(0, StaticJavaParser.parseStatement("super(new DumperOptions());"));
                        ensureImport(cu, "org.yaml.snakeyaml.DumperOptions");
                        changed = true;
                    }
                }

                if (extendsConstructor) {
                    if (hasExplicitSuperInvocation(ctor)) {
                        ExplicitConstructorInvocationStmt invocation = ctor.getBody().getStatements().get(0).asExplicitConstructorInvocationStmt();
                        if (invocation.getArguments().size() == 1) {
                            Expression firstArg = invocation.getArgument(0);
                            if (!isSimpleTypeCreation(firstArg, "LoaderOptions")) {
                                NodeList<Expression> args = new NodeList<>();
                                args.add(firstArg);
                                args.add(StaticJavaParser.parseExpression("new LoaderOptions()"));
                                invocation.setArguments(args);
                                ensureImport(cu, "org.yaml.snakeyaml.LoaderOptions");
                                changed = true;
                            }
                        }
                    }
                }
            }
        }

        return changed;
    }

    private static boolean transformGetPropertiesOverrides(CompilationUnit cu) {
        boolean changed = false;

        for (ClassOrInterfaceDeclaration type : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            if (!(extendsSimpleName(type, "Representer") || extendsSimpleName(type, "BaseRepresenter"))) {
                continue;
            }

            for (MethodDeclaration method : type.getMethods()) {
                if (method.getThrownExceptions().stream().anyMatch(t -> t.asString().endsWith("IntrospectionException"))
                        && (method.getNameAsString().equals("getProperties")
                        || method.findAll(MethodCallExpr.class).stream()
                        .anyMatch(call -> call.getScope().isPresent()
                                && call.getScope().get().isSuperExpr()
                                && call.getNameAsString().equals("getProperties")))) {
                    method.getThrownExceptions().clear();
                    changed = true;
                }
            }
        }

        return changed;
    }

    private static boolean hasExplicitSuperInvocation(ConstructorDeclaration ctor) {
        return !ctor.getBody().getStatements().isEmpty()
                && ctor.getBody().getStatement(0).isExplicitConstructorInvocationStmt();
    }

    private static boolean hasSingleLoaderOptionsArg(ExplicitConstructorInvocationStmt invocation) {
        return invocation.getArguments().size() == 1
                && !isSimpleTypeCreation(invocation.getArgument(0), "LoaderOptions");
    }

    private static boolean extendsSimpleName(ClassOrInterfaceDeclaration type, String name) {
        return type.getExtendedTypes().stream().anyMatch(t -> t.getNameAsString().equals(name));
    }

    private static boolean isSimpleTypeCreation(Expression expression, String typeName) {
        return expression.isObjectCreationExpr()
                && expression.asObjectCreationExpr().getType().getNameAsString().equals(typeName);
    }

    private static void ensureImport(CompilationUnit cu, String fqcn) {
        if (cu.getImports().stream().noneMatch(i -> i.getNameAsString().equals(fqcn))) {
            cu.addImport(fqcn);
        }
    }
}
