package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<File> javaFiles = findJavaFiles(Paths.get(sourceDir));
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int transformedFiles = 0;
            
            for (File javaFile : javaFiles) {
                boolean fileChanged = transformFile(javaFile);
                if (fileChanged) {
                    transformedFiles++;
                }
            }
            
            System.out.println("Transformed " + transformedFiles + " files");
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<File> findJavaFiles(Path startDir) throws IOException {
        List<File> javaFiles = new ArrayList<>();
        Files.walkFileTree(startDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".java")) {
                    javaFiles.add(file.toFile());
                }
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
        return javaFiles;
    }
    
    private static boolean transformFile(File javaFile) throws FileNotFoundException {
        JavaParser parser = new JavaParser();
        Optional<CompilationUnit> cuOpt = parser.parse(javaFile).getResult();
        
        if (!cuOpt.isPresent()) {
            System.err.println("Failed to parse: " + javaFile);
            return false;
        }
        
        CompilationUnit cu = cuOpt.get();
        LogbackTransformer transformer = new LogbackTransformer();
        cu.accept(transformer, null);
        
        if (transformer.wasChanged()) {
            // Add required imports if not already present
            boolean hasReflectImport = cu.getImports().stream()
                .anyMatch(imp -> imp.getNameAsString().equals("java.lang.reflect.Method"));
            if (!hasReflectImport) {
                cu.addImport("java.lang.reflect.Method");
            }
            
            // Save the transformed file
            PrinterConfiguration config = new DefaultPrinterConfiguration();
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter(config);
            String transformedCode = printer.print(cu);
            
            try {
                Files.write(javaFile.toPath(), transformedCode.getBytes());
                System.out.println("Transformed: " + javaFile);
                return true;
            } catch (IOException e) {
                System.err.println("Failed to write file: " + javaFile + ", error: " + e.getMessage());
                return false;
            }
        }
        
        return false;
    }
    
    static class LogbackTransformer extends ModifierVisitor<Void> {
        private boolean changed = false;
        
        public boolean wasChanged() {
            return changed;
        }
        
        @Override
        public Visitable visit(ExpressionStmt n, Void arg) {
            // Check if this statement matches our pattern:
            // ((Logger) LoggerFactory.getLogger(...)).setLevel(...)
            
            Expression expr = n.getExpression();
            if (expr instanceof MethodCallExpr) {
                MethodCallExpr methodCall = (MethodCallExpr) expr;
                if (methodCall.getNameAsString().equals("setLevel")) {
                    // Check if setLevel is called on an expression
                    Optional<Expression> scope = methodCall.getScope();
                    if (scope.isPresent()) {
                        Expression scopeExpr = scope.get();
                        
                        // Handle EnclosedExpr (parentheses)
                        if (scopeExpr instanceof EnclosedExpr) {
                            EnclosedExpr enclosed = (EnclosedExpr) scopeExpr;
                            scopeExpr = enclosed.getInner();
                        }
                        
                        // Check if it's a cast to Logger
                        if (scopeExpr instanceof CastExpr) {
                            CastExpr castExpr = (CastExpr) scopeExpr;
                            if (castExpr.getType().toString().equals("Logger")) {
                                // Found our pattern! Transform it
                                return transformSetLevelCall(n, methodCall, castExpr);
                            }
                        }
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        private BlockStmt transformSetLevelCall(ExpressionStmt stmt, MethodCallExpr methodCall, CastExpr castExpr) {
            changed = true;
            
            // Get the Level argument
            Expression levelArg = methodCall.getArgument(0);
            
            // Get the getLogger expression
            Expression getLoggerExpr = castExpr.getExpression();
            
            // Create reflection code
            // Object logger = LoggerFactory.getLogger(...);
            // try {
            //     Method setLevelMethod = logger.getClass().getMethod("setLevel", Level.class);
            //     setLevelMethod.invoke(logger, level);
            // } catch (Exception e) {
            //     e.printStackTrace();
            // }
            
            // Create variable declaration
            com.github.javaparser.ast.body.VariableDeclarator loggerVar = 
                new com.github.javaparser.ast.body.VariableDeclarator(
                    new ClassOrInterfaceType("Object"),
                    "logger",
                    getLoggerExpr
                );
            VariableDeclarationExpr varDecl = new VariableDeclarationExpr(loggerVar);
            
            // Create Method setLevelMethod = logger.getClass().getMethod("setLevel", Level.class);
            MethodCallExpr getClassCall = new MethodCallExpr(new NameExpr("logger"), "getClass");
            
            // Create Level.class expression
            Expression levelClassExpr;
            if (levelArg instanceof FieldAccessExpr) {
                FieldAccessExpr levelField = (FieldAccessExpr) levelArg;
                levelClassExpr = new FieldAccessExpr(levelField.getScope(), "class");
            } else {
                levelClassExpr = new FieldAccessExpr(new NameExpr("Level"), "class");
            }
            
            MethodCallExpr getMethodCall = new MethodCallExpr(
                getClassCall,
                "getMethod",
                new com.github.javaparser.ast.NodeList<>(
                    new StringLiteralExpr("setLevel"),
                    levelClassExpr
                )
            );
            
            com.github.javaparser.ast.body.VariableDeclarator methodVar = 
                new com.github.javaparser.ast.body.VariableDeclarator(
                    new ClassOrInterfaceType("Method"),
                    "setLevelMethod",
                    getMethodCall
                );
            VariableDeclarationExpr methodVarDecl = new VariableDeclarationExpr(methodVar);
            
            // Create setLevelMethod.invoke(logger, level)
            MethodCallExpr invokeCall = new MethodCallExpr(
                new NameExpr("setLevelMethod"),
                "invoke",
                new com.github.javaparser.ast.NodeList<>(
                    new NameExpr("logger"),
                    levelArg
                )
            );
            
            // Create try-catch block
            BlockStmt tryBlock = new BlockStmt();
            tryBlock.addStatement(new ExpressionStmt(methodVarDecl));
            tryBlock.addStatement(new ExpressionStmt(invokeCall));
            
            BlockStmt catchBlock = new BlockStmt();
            MethodCallExpr printStackTrace = new MethodCallExpr(new NameExpr("e"), "printStackTrace");
            catchBlock.addStatement(new ExpressionStmt(printStackTrace));
            
            com.github.javaparser.ast.stmt.CatchClause catchClause = 
                new com.github.javaparser.ast.stmt.CatchClause(
                    new com.github.javaparser.ast.body.Parameter(
                        new ClassOrInterfaceType("Exception"),
                        "e"
                    ),
                    catchBlock
                );
            
            TryStmt tryStmt = new TryStmt();
            tryStmt.setTryBlock(tryBlock);
            tryStmt.setCatchClauses(new com.github.javaparser.ast.NodeList<>(catchClause));
            
            // Create block with both statements
            BlockStmt block = new BlockStmt();
            block.addStatement(new ExpressionStmt(varDecl));
            block.addStatement(tryStmt);
            
            // Replace the expression statement with our block
            return block;
        }
        
        @Override
        public Visitable visit(FieldAccessExpr n, Void arg) {
            // Also fix Logger.ROOT_LOGGER_NAME if we see it
            if (n.getScope() instanceof NameExpr) {
                NameExpr scope = (NameExpr) n.getScope();
                if (scope.getNameAsString().equals("Logger") && 
                    n.getNameAsString().equals("ROOT_LOGGER_NAME")) {
                    changed = true;
                    return new FieldAccessExpr(
                        new NameExpr("org.slf4j.Logger"), 
                        "ROOT_LOGGER_NAME"
                    );
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(NameExpr n, Void arg) {
            // Handle unqualified ROOT_LOGGER_NAME references
            if (n.getNameAsString().equals("ROOT_LOGGER_NAME")) {
                changed = true;
                return new FieldAccessExpr(
                    new NameExpr("org.slf4j.Logger"),
                    "ROOT_LOGGER_NAME"
                );
            }
            return super.visit(n, arg);
        }
    }
}