package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.configuration.PrettyPrinterConfiguration;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) throws FileNotFoundException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        System.out.println("Processing source directory: " + sourceDir);
        
        // Setup JavaParser with symbol solver
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        JavaParser javaParser = new JavaParser();
        javaParser.getParserConfiguration().setSymbolResolver(symbolSolver);
        
        // Process all Java files
        List<File> javaFiles = findJavaFiles(sourceDir.toFile());
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int transformedFiles = 0;
        int transformedPatterns = 0;
        int removedImports = 0;
        
        for (File file : javaFiles) {
            try {
                CompilationUnit cu = javaParser.parse(file).getResult().orElseThrow();
                boolean fileModified = false;
                
                // Remove cactoos imports
                List<ImportDeclaration> importsToRemove = new ArrayList<>();
                for (ImportDeclaration importDecl : cu.getImports()) {
                    String importName = importDecl.getNameAsString();
                    if (importName.equals("org.cactoos.io.BytesOf") || 
                        importName.equals("org.cactoos.text.HexOf") ||
                        importName.startsWith("org.cactoos.")) {
                        importsToRemove.add(importDecl);
                    }
                }
                
                if (!importsToRemove.isEmpty()) {
                    for (ImportDeclaration importDecl : importsToRemove) {
                        cu.remove(importDecl);
                        removedImports++;
                    }
                    fileModified = true;
                }
                
                // Transform HexOf(BytesOf(...)).asString() and ListOf patterns
                HexBytesVisitor visitor = new HexBytesVisitor();
                cu.accept(visitor, null);
                if (visitor.getTransformCount() > 0) {
                    transformedPatterns += visitor.getTransformCount();
                    fileModified = true;
                    
                    // Add imports that might be needed after transformation
                    // Add import for Hex if not already present
                    boolean hasHexImport = false;
                    boolean hasArraysImport = false;
                    for (ImportDeclaration importDecl : cu.getImports()) {
                        String importName = importDecl.getNameAsString();
                        if (importName.equals("org.apache.commons.codec.binary.Hex")) {
                            hasHexImport = true;
                        }
                        if (importName.equals("java.util.Arrays")) {
                            hasArraysImport = true;
                        }
                    }
                    if (!hasHexImport) {
                        cu.addImport("org.apache.commons.codec.binary.Hex");
                    }
                    if (!hasArraysImport) {
                        cu.addImport("java.util.Arrays");
                    }
                }
                
                if (fileModified) {
                    // Write modified file
                    PrettyPrinterConfiguration config = new PrettyPrinterConfiguration();
                    config.setPrintComments(true);
                    config.setEndOfLineCharacter("\n");
                    
                    String newContent = cu.toString(config);
                    java.nio.file.Files.write(file.toPath(), newContent.getBytes());
                    transformedFiles++;
                    System.out.println("Transformed file: " + file);
                }
                
            } catch (Exception e) {
                System.err.println("Error processing file " + file + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("\nTransformation summary:");
        System.out.println("  Files processed: " + javaFiles.size());
        System.out.println("  Files transformed: " + transformedFiles);
        System.out.println("  Patterns transformed: " + transformedPatterns);
        System.out.println("  Imports removed: " + removedImports);
    }
    
    private static List<File> findJavaFiles(File dir) {
        List<File> javaFiles = new ArrayList<>();
        findJavaFilesRecursive(dir, javaFiles);
        return javaFiles;
    }
    
    private static void findJavaFilesRecursive(File dir, List<File> javaFiles) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        findJavaFilesRecursive(file, javaFiles);
                    } else if (file.getName().endsWith(".java")) {
                        javaFiles.add(file);
                    }
                }
            }
        }
    }
    
    static class HexBytesVisitor extends ModifierVisitor<Void> {
        private int transformCount = 0;
        
        public int getTransformCount() {
            return transformCount;
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Look for pattern: new HexOf(new BytesOf(expression)).asString()
            if (n.getNameAsString().equals("asString") && n.getScope().isPresent()) {
                Expression scope = n.getScope().get();
                
                // Check if scope is ObjectCreationExpr for HexOf
                if (scope instanceof ObjectCreationExpr) {
                    ObjectCreationExpr hexOfCreation = (ObjectCreationExpr) scope;
                    if (hexOfCreation.getTypeAsString().equals("HexOf") && 
                        hexOfCreation.getArguments().size() == 1) {
                        
                        Expression hexOfArg = hexOfCreation.getArgument(0);
                        
                        // Check if argument is ObjectCreationExpr for BytesOf
                        if (hexOfArg instanceof ObjectCreationExpr) {
                            ObjectCreationExpr bytesOfCreation = (ObjectCreationExpr) hexOfArg;
                            if (bytesOfCreation.getTypeAsString().equals("BytesOf") &&
                                bytesOfCreation.getArguments().size() == 1) {
                                
                                // Found pattern: new HexOf(new BytesOf(expression)).asString()
                                Expression innerExpr = bytesOfCreation.getArgument(0);
                                
                                // Replace with: Hex.encodeHexString(innerExpr)
                                MethodCallExpr replacement = new MethodCallExpr(
                                    new NameExpr("Hex"),
                                    "encodeHexString"
                                );
                                replacement.addArgument(innerExpr);
                                
                                transformCount++;
                                return replacement;
                            }
                        }
                    }
                }
            }
            
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Look for pattern: new ListOf(...)
            if (n.getTypeAsString().equals("ListOf")) {
                // Replace new ListOf(...) with Arrays.asList(...)
                MethodCallExpr replacement = new MethodCallExpr(
                    new NameExpr("Arrays"),
                    "asList"
                );
                for (Expression argExpr : n.getArguments()) {
                    replacement.addArgument(argExpr);
                }
                
                transformCount++;
                return replacement;
            }
            
            return super.visit(n, arg);
        }
    }
}