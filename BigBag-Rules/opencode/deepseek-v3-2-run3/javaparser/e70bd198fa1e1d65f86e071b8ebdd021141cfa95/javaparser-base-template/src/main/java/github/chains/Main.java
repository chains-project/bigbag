package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -cp target/classes github.chains.Main <source-directory>");
            System.exit(1);
        }

        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }

        System.out.println("Processing source files in: " + sourceDir);
        
        AtomicInteger filesProcessed = new AtomicInteger(0);
        AtomicInteger transformationsApplied = new AtomicInteger(0);
        
        Files.walk(sourcePath)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(filePath -> {
                try {
                    boolean changed = processFile(filePath);
                    if (changed) {
                        transformationsApplied.incrementAndGet();
                    }
                    filesProcessed.incrementAndGet();
                } catch (IOException e) {
                    System.err.println("Error processing file: " + filePath + " - " + e.getMessage());
                }
            });
        
        System.out.println("Processed " + filesProcessed.get() + " files");
        System.out.println("Applied transformations to " + transformationsApplied.get() + " files");
    }

    private static boolean processFile(Path filePath) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(filePath).getResult().orElse(null);
        
        if (cu == null) {
            return false;
        }

        TinspinTransformationVisitor visitor = new TinspinTransformationVisitor();
        cu.accept(visitor, null);
        
        if (visitor.fileChanged) {
            PrinterConfiguration config = new DefaultPrinterConfiguration();
            
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(cu.toString(config));
            }
            
            System.out.println("Modified: " + filePath);
            return true;
        }
        
        return false;
    }

    private static class TinspinTransformationVisitor extends ModifierVisitor<Void> {
        boolean fileChanged = false;
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            
            // Fix import changes
            if (importName.equals("org.tinspin.index.PointIndex")) {
                fileChanged = true;
                return new ImportDeclaration("org.tinspin.index.PointMap", importDecl.isStatic(), importDecl.isAsterisk());
            } else if (importName.equals("org.tinspin.index.PointDistanceFunction")) {
                fileChanged = true;
                return new ImportDeclaration("org.tinspin.index.PointDistance", importDecl.isStatic(), importDecl.isAsterisk());
            } else if (importName.equals("org.tinspin.index.PointEntryDist")) {
                fileChanged = true;
                ImportDeclaration newImport = new ImportDeclaration("org.tinspin.index.Index.PointEntryKnn", importDecl.isStatic(), importDecl.isAsterisk());
                newImport.setAsterisk(false);
                fileChanged = true;
                return newImport;
            }
            
            return super.visit(importDecl, arg);
        }
        
        @Override
        public Node visit(MethodCallExpr methodCall, Void arg) {
            String methodName = methodCall.getNameAsString();
            
            // Fix method name change from query1NN to query1nn
            if (methodName.equals("query1NN")) {
                fileChanged = true;
                methodCall.setName("query1nn");
            }
            
            // Fix KDTree.create() calls with lambda arguments
            if (methodName.equals("create")) {
                Node parent = methodCall.getParentNode().orElse(null);
                if (parent != null && parent.toString().contains("KDTree")) {
                    if (methodCall.getArguments().size() == 2) {
                        // Check if second argument is a lambda
                        if (methodCall.getArgument(1).toString().contains("->")) {
                            fileChanged = true;
                            // Remove the lambda argument
                            methodCall.getArguments().remove(1);
                        }
                    }
                }
            }
            
            return (Node) super.visit(methodCall, arg);
        }
        
        @Override
        public Node visit(ClassOrInterfaceType type, Void arg) {
            String typeName = type.getNameAsString();
            
            // Fix type name changes
            if (typeName.equals("PointIndex")) {
                fileChanged = true;
                type.setName("PointMap");
            } else if (typeName.equals("PointDistanceFunction")) {
                fileChanged = true;
                type.setName("PointDistance");
            } else if (typeName.equals("PointEntryDist")) {
                fileChanged = true;
                // When PointEntryDist is used as a type, we need to check if it's qualified
                // If it's already qualified (like org.tinspin.index.PointEntryDist), we should import
                // Index.PointEntryKnn and use PointEntryKnn
                // For simplicity, change to PointEntryKnn (assuming import will be added)
                type.setName("PointEntryKnn");
            } else if (typeName.equals("Index.PointEntryKnn")) {
                // Change qualified name to simple name
                fileChanged = true;
                type.setName("PointEntryKnn");
            }
            
            return (Node) super.visit(type, arg);
        }
    }
}