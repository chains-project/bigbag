package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.DefaultPrettyPrinterVisitor;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }

        Path sourceDir = Paths.get(args[0]);
        System.out.println("Processing source directory: " + sourceDir);

        Files.walk(sourceDir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
        
        System.out.println("Transformation complete!");
    }

    private static void processFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            JavaParser parser = new JavaParser();
            Optional<CompilationUnit> cuOpt = parser.parse(content).getResult();
            
            if (cuOpt.isEmpty()) {
                System.err.println("Failed to parse: " + filePath);
                return;
            }

            CompilationUnit cu = cuOpt.get();
            
            TinspinApiUpdater visitor = new TinspinApiUpdater();
            cu.accept(visitor, null);
            
            String transformed = cu.toString();
            if (!transformed.equals(content)) {
                Files.writeString(filePath, transformed);
                System.out.println("Updated: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }

    static class TinspinApiUpdater extends ModifierVisitor<Void> {
        
        @Override
        public Node visit(ImportDeclaration n, Void arg) {
            String name = n.getNameAsString();
            
            if (name.equals("org.tinspin.index.PointIndex")) {
                n.setName("org.tinspin.index.PointMap");
            }
            
            if (name.equals("org.tinspin.index.PointDistanceFunction")) {
                n.setName("org.tinspin.index.PointDistance");
            }
            
            if (name.equals("org.tinspin.index.PointEntryDist")) {
                n.setName("org.tinspin.index.Index");
            }
            
            super.visit(n, arg);
            return n;
        }

        @Override
        public Node visit(ClassOrInterfaceType n, Void arg) {
            String typeName = n.getNameAsString();
            
            if (typeName.equals("PointIndex")) {
                n.setName("PointMap");
            }
            
            if (typeName.equals("PointDistanceFunction")) {
                n.setName("PointDistance");
            }
            
            if (typeName.equals("PointEntryDist")) {
                n.setName("Index.PointEntryKnn");
            }
            
            if (typeName.contains(".") && typeName.endsWith(".PointEntryDist")) {
                String base = typeName.substring(0, typeName.length() - ".PointEntryDist".length());
                n.setName(base + ".Index.PointEntryKnn");
            }
            
            super.visit(n, arg);
            return n;
        }

        @Override
        public Node visit(MethodCallExpr n, Void arg) {
            String methodName = n.getNameAsString();
            
            if (methodName.equals("query1NN")) {
                n.setName("query1nn");
            }
            
            if (methodName.equals("create")) {
                Optional<Expression> scope = n.getScope();
                if (scope.isPresent() && scope.get() instanceof NameExpr) {
                    String scopeName = ((NameExpr) scope.get()).getNameAsString();
                    if (scopeName.equals("KDTree") || scopeName.equals("CoverTree")) {
                        NodeList<Expression> arguments = n.getArguments();
                        if (arguments.size() == 2) {
                            Expression secondArg = arguments.get(1);
                            if (secondArg instanceof LambdaExpr) {
                                LambdaExpr lambda = (LambdaExpr) secondArg;
                                if (isEuclideanDistanceLambda(lambda)) {
                                    arguments.remove(1);
                                }
                            }
                        }
                    }
                }
            }
            
            super.visit(n, arg);
            return n;
        }
        
        private boolean isEuclideanDistanceLambda(LambdaExpr lambda) {
            try {
                String lambdaBody = lambda.getBody().toString();
                return lambdaBody.contains("Math.sqrt") || 
                       (lambdaBody.contains("*") && lambdaBody.contains("+") && 
                        lambdaBody.contains("-") && lambdaBody.contains("p1") && lambdaBody.contains("p2"));
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public Node visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            return n;
        }

        @Override
        public Node visit(com.github.javaparser.ast.body.FieldDeclaration n, Void arg) {
            Type type = n.getElementType();
            if (type instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType classType = (ClassOrInterfaceType) type;
                String typeName = classType.getNameAsString();
                
                if (typeName.equals("PointDistanceFunction")) {
                    classType.setName("PointDistance");
                }
            }
            super.visit(n, arg);
            return n;
        }
    }
}