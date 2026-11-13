package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.Node;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        transformProject(sourceDir);
    }
    
    private static void transformProject(Path sourceDir) {
        try {
            Files.walk(sourceDir)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(Main::transformFile);
        } catch (Exception e) {
            System.err.println("Error traversing directory: " + e.getMessage());
        }
    }
    
    private static void transformFile(Path javaFile) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow();
            
            TinspinTransformer transformer = new TinspinTransformer();
            cu.accept(transformer, null);
            
            String transformedCode = cu.toString();
            
            Files.write(javaFile, transformedCode.getBytes());
            System.out.println("Transformed: " + javaFile);
        } catch (Exception e) {
            System.err.println("Error transforming " + javaFile + ": " + e.getMessage());
        }
    }
    
    private static class TinspinTransformer extends ModifierVisitor<Void> {
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            
            // Transform imports
            if (importName.equals("org.tinspin.index.PointIndex")) {
                importDecl.setName("org.tinspin.index.PointMap");
            } else if (importName.equals("org.tinspin.index.PointDistanceFunction")) {
                importDecl.setName("org.tinspin.index.PointDistance");
            } else if (importName.equals("org.tinspin.index.PointEntryDist")) {
                importDecl.setName("org.tinspin.index.Index.PointEntryKnn");
            }
            
            return (Node) super.visit(importDecl, arg);
        }
        
        @Override
        public Node visit(ClassOrInterfaceType type, Void arg) {
            String typeName = type.getNameAsString();
            
            // Transform type references
            if (typeName.equals("PointIndex")) {
                type.setName("PointMap");
            } else if (typeName.equals("PointDistanceFunction")) {
                type.setName("PointDistance");
            } else if (typeName.equals("PointEntryDist")) {
                type.setName("PointEntryKnn");
            }
            
            // Also check if the type is qualified with org.tinspin.index
            if (type.getScope().isPresent()) {
                String scope = type.getScope().get().toString();
                if (scope.equals("org.tinspin.index") || scope.equals("org.tinspin.index.Index")) {
                    if (typeName.equals("PointEntryDist")) {
                        type.setName("PointEntryKnn");
                    }
                }
            }
            
            return (Node) super.visit(type, arg);
        }
        
        @Override
        public Node visit(MethodCallExpr methodCall, Void arg) {
            String methodName = methodCall.getNameAsString();
            
            // Transform method calls
            if (methodName.equals("query1NN")) {
                methodCall.setName("query1nn");
            }
            
            // Handle KDTree.create(int, PointDistanceFunction) -> KDTree.create(int)
            // This transformation removes the PointDistanceFunction parameter which is no longer supported
            if (methodName.equals("create") && methodCall.getScope().isPresent()) {
                String scope = methodCall.getScope().get().toString();
                if (scope.equals("KDTree") || scope.endsWith(".KDTree")) {
                    if (methodCall.getArguments().size() == 2) {
                        // In tinspin-indexes 2.0.0, KDTree.create() no longer accepts PointDistanceFunction
                        // Remove the second argument
                        NodeList arguments = new NodeList();
                        arguments.add(methodCall.getArgument(0));
                        methodCall.setArguments(arguments);
                    }
                }
            }
            
            return (Node) super.visit(methodCall, arg);
        }
    }
}