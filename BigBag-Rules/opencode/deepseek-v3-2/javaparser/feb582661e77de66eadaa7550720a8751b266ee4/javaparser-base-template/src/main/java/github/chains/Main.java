package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.DefaultPrettyPrinterVisitor;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    
    public static class StringUtilsImportTransformer extends ModifierVisitor<Void> {
        @Override
        public ImportDeclaration visit(ImportDeclaration importDecl, Void arg) {
            super.visit(importDecl, arg);
            String importName = importDecl.getNameAsString();
            if (importName.equals("liquibase.util.StringUtils")) {
                importDecl.setName("liquibase.repackaged.org.apache.commons.lang3.StringUtils");
            }
            return importDecl;
        }
    }
    
    public static class ExecutorServiceTransformer extends ModifierVisitor<Void> {
        private boolean addedScopeImport = false;
        
        @Override
        public Visitable visit(CompilationUnit cu, Void arg) {
            addedScopeImport = false;
            Visitable result = super.visit(cu, arg);
            
            if (addedScopeImport) {
                boolean hasScopeImport = false;
                for (ImportDeclaration importDecl : cu.getImports()) {
                    if (importDecl.getNameAsString().equals("liquibase.Scope")) {
                        hasScopeImport = true;
                        break;
                    }
                }
                if (!hasScopeImport) {
                    cu.addImport("liquibase.Scope");
                }
            }
            return result;
        }
        
        @Override
        public Visitable visit(MethodCallExpr methodCall, Void arg) {
            methodCall = (MethodCallExpr) super.visit(methodCall, arg);
            
            if (isExecutorServiceGetInstanceCall(methodCall)) {
                return replaceExecutorServiceGetInstance(methodCall);
            }
            
            if (isChainedExecutorServiceCall(methodCall)) {
                return replaceChainedExecutorServiceCall(methodCall);
            }
            
            return methodCall;
        }
        
        private boolean isExecutorServiceGetInstanceCall(MethodCallExpr methodCall) {
            return methodCall.getNameAsString().equals("getInstance") &&
                   methodCall.getScope().isPresent() &&
                   methodCall.getScope().get() instanceof NameExpr &&
                   ((NameExpr) methodCall.getScope().get()).getNameAsString().equals("ExecutorService");
        }
        
        private boolean isChainedExecutorServiceCall(MethodCallExpr methodCall) {
            if (methodCall.getNameAsString().equals("getExecutor") &&
                methodCall.getScope().isPresent() &&
                methodCall.getScope().get() instanceof MethodCallExpr) {
                
                MethodCallExpr parentCall = (MethodCallExpr) methodCall.getScope().get();
                return isExecutorServiceGetInstanceCall(parentCall);
            }
            return false;
        }
        
        private MethodCallExpr replaceExecutorServiceGetInstance(MethodCallExpr methodCall) {
            addedScopeImport = true;
            
            MethodCallExpr getCurrentScope = new MethodCallExpr(
                new NameExpr("Scope"),
                "getCurrentScope"
            );
            
            return new MethodCallExpr(
                getCurrentScope,
                "getSingleton",
                new NodeList<>(
                    new ClassExpr(new ClassOrInterfaceType(null, "ExecutorService"))
                )
            );
        }
        
        private MethodCallExpr replaceChainedExecutorServiceCall(MethodCallExpr methodCall) {
            addedScopeImport = true;
            MethodCallExpr parentCall = (MethodCallExpr) methodCall.getScope().get();
            
            MethodCallExpr getCurrentScope = new MethodCallExpr(
                new NameExpr("Scope"),
                "getCurrentScope"
            );
            
            MethodCallExpr getSingleton = new MethodCallExpr(
                getCurrentScope,
                "getSingleton",
                new NodeList<>(
                    new ClassExpr(new ClassOrInterfaceType(null, "ExecutorService"))
                )
            );
            
            MethodCallExpr newGetExecutor = new MethodCallExpr(
                getSingleton,
                "getExecutor"
            );
            
            newGetExecutor.setArguments(methodCall.getArguments());
            return newGetExecutor;
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Example: java github.chains.Main /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Transforming Java files in: " + sourceDir);
        
        try {
            List<Path> javaFiles = findJavaFiles(sourcePath);
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            int transformedFiles = 0;
            int stringUtilsTransforms = 0;
            int executorServiceTransforms = 0;
            
            JavaParser javaParser = new JavaParser();
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
            
            for (Path javaFile : javaFiles) {
                boolean fileTransformed = false;
                
                try {
                    String originalContent = Files.readString(javaFile);
                    CompilationUnit cu = javaParser.parse(javaFile).getResult().orElse(null);
                    if (cu == null) {
                        System.err.println("Warning: Failed to parse " + javaFile);
                        continue;
                    }
                    
                    StringUtilsImportTransformer stringUtilsTransformer = new StringUtilsImportTransformer();
                    ExecutorServiceTransformer executorServiceTransformer = new ExecutorServiceTransformer();
                    
                    cu.accept(stringUtilsTransformer, null);
                    cu.accept(executorServiceTransformer, null);
                    
                    String transformedContent = printer.print(cu);
                    
                    if (!originalContent.equals(transformedContent)) {
                        Files.writeString(javaFile, transformedContent);
                        fileTransformed = true;
                        transformedFiles++;
                        
                        if (!originalContent.contains("liquibase.repackaged.org.apache.commons.lang3.StringUtils") && 
                            transformedContent.contains("liquibase.repackaged.org.apache.commons.lang3.StringUtils")) {
                            stringUtilsTransforms++;
                        }
                        
                        if (originalContent.contains("ExecutorService.getInstance()") && 
                            !transformedContent.contains("ExecutorService.getInstance()")) {
                            executorServiceTransforms++;
                        }
                    }
                    
                } catch (IOException e) {
                    System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
                } catch (Exception e) {
                    System.err.println("Unexpected error processing file " + javaFile + ": " + e.getMessage());
                    e.printStackTrace();
                }
                
                if (fileTransformed) {
                    System.out.println("  Transformed: " + sourcePath.relativize(javaFile));
                }
            }
            
            System.out.println("\nTransformation complete!");
            System.out.println("Total files transformed: " + transformedFiles);
            System.out.println("StringUtils import updates: " + stringUtilsTransforms);
            System.out.println("ExecutorService.getInstance() replacements: " + executorServiceTransforms);
            
        } catch (IOException e) {
            System.err.println("Error walking directory: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(Path directory) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        return javaFiles;
    }
}