package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException {
        Path root = Paths.get("/workspace/java-api");
        Map<String, String> importReplacements = new HashMap<>();
        importReplacements.put("org.cactoos.iterable.LengthOf", "org.cactoos.scalar.LengthOf");
        importReplacements.put("org.cactoos.collection.CollectionOf", "org.cactoos.list.ListOf");
        importReplacements.put("org.cactoos.text.RandomText", "org.cactoos.text.Randomized");
        importReplacements.put("org.cactoos.collection.Filtered", "org.cactoos.iterable.Filtered");
        importReplacements.put("org.cactoos.scalar.CheckedScalar", "org.cactoos.scalar.Checked");
        importReplacements.put("org.cactoos.scalar.UncheckedScalar", "org.cactoos.scalar.Unchecked");
        importReplacements.put("org.cactoos.text.SplitText", "org.cactoos.text.Split");
        importReplacements.put("org.cactoos.scalar.IoCheckedScalar", "org.cactoos.scalar.IoChecked");
        importReplacements.put("org.cactoos.scalar.SolidScalar", "org.cactoos.scalar.Solid");
        importReplacements.put("org.cactoos.text.JoinedText", "org.cactoos.text.Joined");
        importReplacements.put("org.cactoos.scalar.StickyScalar", "org.cactoos.scalar.Sticky");
        importReplacements.put("org.cactoos.text.TrimmedText", "org.cactoos.text.Trimmed");
        importReplacements.put("org.cactoos.func.IoCheckedFunc", "org.cactoos.Func");

        Map<String, String> classRenames = new HashMap<>();
        classRenames.put("CollectionOf", "ListOf");
        classRenames.put("RandomText", "Randomized");
        classRenames.put("CheckedScalar", "Checked");
        classRenames.put("UncheckedScalar", "Unchecked");
        classRenames.put("SplitText", "Split");
        classRenames.put("IoCheckedScalar", "IoChecked");
        classRenames.put("SolidScalar", "Solid");
        classRenames.put("JoinedText", "Joined");
        classRenames.put("StickyScalar", "Sticky");
        classRenames.put("TrimmedText", "Trimmed");
        classRenames.put("IoCheckedFunc", "Func");

        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.toString().endsWith(".java") && !p.toString().contains("/target/"))
                .forEach(path -> {
                    try {
                        String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                        CompilationUnit cu = StaticJavaParser.parse(content);
                        boolean modified = false;

                        // Imports
                        for (ImportDeclaration imp : cu.getImports()) {
                            String name = imp.getNameAsString();
                            if (importReplacements.containsKey(name)) {
                                imp.setName(importReplacements.get(name));
                                modified = true;
                            }
                        }

                        // Class Renames in Types
                        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
                            String name = type.getNameAsString();
                            if (classRenames.containsKey(name)) {
                                type.setName(classRenames.get(name));
                                modified = true;
                            }
                        }
                        
                        // Object Creation Renames
                         for (ObjectCreationExpr obj : cu.findAll(ObjectCreationExpr.class)) {
                            String name = obj.getType().getNameAsString();
                            if (classRenames.containsKey(name)) {
                                obj.getType().setName(classRenames.get(name));
                                modified = true;
                            }
                        }

                        // Special case for LengthOf.intValue() -> LengthOf.value().intValue()
                        for (MethodCallExpr method : cu.findAll(MethodCallExpr.class)) {
                            if (method.getNameAsString().equals("intValue")) {
                                if (method.getScope().isPresent() && method.getScope().get() instanceof ObjectCreationExpr) {
                                    ObjectCreationExpr obj = (ObjectCreationExpr) method.getScope().get();
                                    if (obj.getType().getNameAsString().equals("LengthOf")) {
                                        if (!(method.getParentNode().isPresent() && method.getParentNode().get() instanceof MethodCallExpr && ((MethodCallExpr)method.getParentNode().get()).getNameAsString().equals("value"))) {
                                            MethodCallExpr valueCall = new MethodCallExpr(obj, "value");
                                            method.setScope(valueCall);
                                            modified = true;
                                        }
                                    }
                                }
                            }
                        }

                        // IterableEnvelope super(() -> ...) -> super(...)
                        for (ExplicitConstructorInvocationStmt stmt : cu.findAll(ExplicitConstructorInvocationStmt.class)) {
                            if (stmt.getArguments().size() == 1 && stmt.getArgument(0) instanceof LambdaExpr) {
                                LambdaExpr lambda = (LambdaExpr) stmt.getArgument(0);
                                if (lambda.getParameters().isEmpty()) {
                                    if (lambda.getBody() instanceof ExpressionStmt) {
                                        stmt.setArgument(0, ((ExpressionStmt) lambda.getBody()).getExpression());
                                        modified = true;
                                    } else if (lambda.getExpressionBody().isPresent()) {
                                        stmt.setArgument(0, lambda.getExpressionBody().get());
                                        modified = true;
                                    }
                                }
                            }
                        }

                        // Filtered.isEmpty() -> new ListOf<>(Filtered).isEmpty()
                        for (MethodCallExpr m : cu.findAll(MethodCallExpr.class)) {
                            if (m.getNameAsString().equals("isEmpty") && m.getScope().isPresent() && m.getScope().get() instanceof ObjectCreationExpr) {
                                ObjectCreationExpr obj = (ObjectCreationExpr) m.getScope().get();
                                if (obj.getType().getNameAsString().equals("Filtered")) {
                                     // wrap obj in new ListOf<>(obj)
                                     ClassOrInterfaceType listOfType = new ClassOrInterfaceType(null, "ListOf");
                                     listOfType.setTypeArguments(new NodeList<>()); // Diamond <>
                                     ObjectCreationExpr listOf = new ObjectCreationExpr(null, listOfType, new NodeList<>(obj));
                                     m.setScope(listOf);
                                     modified = true;
                                }
                            }
                        }

                        // Mapped<A, B> -> Mapped<B>
                        for (ObjectCreationExpr obj : cu.findAll(ObjectCreationExpr.class)) {
                            if (obj.getType().getNameAsString().equals("Mapped")) {
                                if (obj.getType().getTypeArguments().isPresent() && obj.getType().getTypeArguments().get().size() == 2) {
                                    obj.getType().getTypeArguments().get().remove(0); // Remove first
                                    modified = true;
                                }
                            }
                        }
                        
                        // IterableOf(copies) -> ListOf(copies) in Copies.java
                        for (ObjectCreationExpr obj : cu.findAll(ObjectCreationExpr.class)) {
                            if (obj.getType().getNameAsString().equals("IterableOf") && obj.getArguments().size() == 1 && obj.getArgument(0).toString().equals("copies")) {
                                obj.getType().setName("ListOf");
                                modified = true;
                            }
                        }

                        // Skipped<> -> Skipped<Text>
                        for (ObjectCreationExpr obj : cu.findAll(ObjectCreationExpr.class)) {
                            if (obj.getType().getNameAsString().equals("Skipped")) {
                                if (!obj.getType().getTypeArguments().isPresent() || obj.getType().getTypeArguments().get().isEmpty()) {
                                    NodeList<Type> typeArgs = new NodeList<>();
                                    typeArgs.add(new ClassOrInterfaceType(null, "Text"));
                                    obj.getType().setTypeArguments(typeArgs);
                                    
                                    boolean hasTextImport = cu.getImports().stream().anyMatch(i -> i.getNameAsString().equals("org.cactoos.Text"));
                                    if (!hasTextImport) {
                                        cu.addImport("org.cactoos.Text");
                                    }
                                    
                                    modified = true;
                                }
                                
                                // Swap arguments for Skipped: (Iterable, int) -> (int, Iterable)
                                if (obj.getArguments().size() == 2) {
                                    Expression arg0 = obj.getArgument(0);
                                    Expression arg1 = obj.getArgument(1);
                                    if (arg1.isIntegerLiteralExpr()) {
                                         NodeList<Expression> constructorArgs = new NodeList<>();
                                         constructorArgs.add(arg1);
                                         constructorArgs.add(arg0);
                                         obj.setArguments(constructorArgs);
                                         modified = true;
                                    }
                                }
                            }
                        }
                        
                        // Ensure ListOf has diamond
                        for (ObjectCreationExpr obj : cu.findAll(ObjectCreationExpr.class)) {
                            if (obj.getType().getNameAsString().equals("ListOf")) {
                                if (!obj.getType().getTypeArguments().isPresent()) {
                                    obj.getType().setTypeArguments(new NodeList<>());
                                    modified = true;
                                }
                            }
                        }
                        
                        // Add throws IOException to Copies constructor
                        for (ConstructorDeclaration constructor : cu.findAll(ConstructorDeclaration.class)) {
                            if (constructor.getNameAsString().equals("Copies")) {
                                if (constructor.getThrownExceptions().stream().noneMatch(t -> t.asString().equals("IOException"))) {
                                    constructor.addThrownException(new ClassOrInterfaceType(null, "IOException"));
                                    modified = true;
                                }
                            }
                        }
                        
                        // Add throws Exception to WalletsIn constructor
                        for (ConstructorDeclaration constructor : cu.findAll(ConstructorDeclaration.class)) {
                            if (constructor.getNameAsString().equals("WalletsIn")) {
                                if (constructor.getThrownExceptions().stream().noneMatch(t -> t.asString().equals("Exception"))) {
                                    constructor.addThrownException(new ClassOrInterfaceType(null, "Exception"));
                                    modified = true;
                                }
                            }
                        }
                        
                        // Add throws Exception to WalletsInTest methods
                        if (path.toString().endsWith("WalletsInTest.java")) {
                            for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
                                if (method.getAnnotationByName("Test").isPresent()) {
                                    if (method.getThrownExceptions().stream().noneMatch(t -> t.asString().equals("Exception"))) {
                                        // Remove other exceptions if Exception covers them? No, just add Exception.
                                        method.addThrownException(new ClassOrInterfaceType(null, "Exception"));
                                        modified = true;
                                    }
                                }
                            }
                        }
                        
                        // Replace new IoCheckedFunc(...) with just the lambda
                        for (ObjectCreationExpr obj : cu.findAll(ObjectCreationExpr.class)) {
                            if (obj.getType().getNameAsString().equals("IoCheckedFunc") || obj.getType().getNameAsString().equals("Func")) { 
                                if (obj.getType().getNameAsString().equals("Func")) {
                                    if (obj.getArguments().size() == 1) {
                                        Expression arg = obj.getArgument(0);
                                        obj.replace(arg);
                                        modified = true;
                                    }
                                }
                            }
                        }

                        if (modified) {
                            Files.write(path, cu.toString().getBytes(StandardCharsets.UTF_8));
                            System.out.println("Modified: " + path);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
        }
    }
}