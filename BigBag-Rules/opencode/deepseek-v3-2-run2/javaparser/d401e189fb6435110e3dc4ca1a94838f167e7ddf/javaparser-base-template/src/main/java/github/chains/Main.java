package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.DefaultPrettyPrinter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java -jar javaparser.jar <source-directory> <output-directory>");
            System.out.println("Transformation: Fixes Logger casts broken by LoggingEventAware interface in logback-classic 1.4.4");
            System.out.println("Transforms: ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.X)");
            System.out.println("To: Use reflection to avoid compile-time dependency on missing LoggingEventAware interface");
            return;
        }
        
        Path sourceDir = Paths.get(args[0]);
        Path outputDir = Paths.get(args[1]);
        
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }
        
        List<Path> javaFiles = Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        JavaParser parser = new JavaParser();
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        
        int transformedFiles = 0;
        int[] transformedPatterns = new int[1];
        
        for (Path javaFile : javaFiles) {
            CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse " + javaFile));
            
            boolean modified = cu.accept(new ModifierVisitor<Void>() {
                @Override
                public Visitable visit(MethodCallExpr n, Void arg) {
                    // Look for patterns like: ((Logger) LoggerFactory.getLogger(...)).setLevel(...)
                    if (n.getNameAsString().equals("setLevel") && n.getArguments().size() == 1) {
                        if (n.getScope().isPresent() && n.getScope().get() instanceof CastExpr) {
                            CastExpr castExpr = (CastExpr) n.getScope().get();
                            if (castExpr.getType().asString().equals("Logger")) {
                                if (castExpr.getExpression() instanceof MethodCallExpr) {
                                    MethodCallExpr getLoggerCall = (MethodCallExpr) castExpr.getExpression();
                                    if (getLoggerCall.getNameAsString().equals("getLogger")) {
                                        transformedPatterns[0]++;
                                        
                                        // Get the level argument
                                        Expression levelArg = n.getArgument(0);
                                        
                                        // Create reflection-based replacement
                                        // Try to avoid compile-time dependency on Logger class
                                        // which implements missing LoggingEventAware interface
                                        
                                        // Build reflection code:
                                        // try {
                                        //     Object logger = LoggerFactory.getLogger("ROOT");
                                        //     Class<?> loggerClass = Class.forName("ch.qos.logback.classic.Logger");
                                        //     java.lang.reflect.Method setLevelMethod = loggerClass.getMethod("setLevel", Class.forName("ch.qos.logback.classic.Level"));
                                        //     setLevelMethod.invoke(logger, Level.TRACE);
                                        // } catch (Exception e) {
                                        //     throw new RuntimeException(e);
                                        // }
                                        
                                        // Create the reflection expression
                                        MethodCallExpr forNameCall1 = new MethodCallExpr(
                                            new NameExpr("Class"), 
                                            "forName"
                                        );
                                        forNameCall1.addArgument(new StringLiteralExpr("ch.qos.logback.classic.Logger"));
                                        
                                        MethodCallExpr forNameCall2 = new MethodCallExpr(
                                            new NameExpr("Class"), 
                                            "forName"
                                        );
                                        forNameCall2.addArgument(new StringLiteralExpr("ch.qos.logback.classic.Level"));
                                        
                                        MethodCallExpr getMethodCall = new MethodCallExpr(
                                            new NameExpr("loggerClass"), 
                                            "getMethod"
                                        );
                                        getMethodCall.addArgument(new StringLiteralExpr("setLevel"));
                                        getMethodCall.addArgument(forNameCall2);
                                        
                                        MethodCallExpr invokeCall = new MethodCallExpr(
                                            new NameExpr("setLevelMethod"), 
                                            "invoke"
                                        );
                                        invokeCall.addArgument(new NameExpr("logger"));
                                        invokeCall.addArgument(levelArg.clone());
                                        
                                        // Create variable declarations
                                        VariableDeclarationExpr loggerVar = new VariableDeclarationExpr(
                                            StaticJavaParser.parseClassOrInterfaceType("Object"),
                                            "logger"
                                        );
                                        MethodCallExpr getLoggerExpr = getLoggerCall.clone();
                                        // Fix the argument to use "ROOT" instead of Logger.ROOT_LOGGER_NAME
                                        for (int i = 0; i < getLoggerExpr.getArguments().size(); i++) {
                                            Expression argument = getLoggerExpr.getArgument(i);
                                            if (argument instanceof FieldAccessExpr) {
                                                FieldAccessExpr fieldAccess = (FieldAccessExpr) argument;
                                                if (fieldAccess.getScope() instanceof NameExpr) {
                                                    NameExpr scope = (NameExpr) fieldAccess.getScope();
                                                    if (scope.getNameAsString().equals("Logger") && 
                                                        fieldAccess.getNameAsString().equals("ROOT_LOGGER_NAME")) {
                                                        getLoggerExpr.setArgument(i, new StringLiteralExpr("ROOT"));
                                                    }
                                                }
                                            }
                                        }
                                        
                                        AssignmentExpr assignLogger = new AssignmentExpr(
                                            new NameExpr("logger"),
                                            getLoggerExpr,
                                            AssignmentExpr.Operator.ASSIGN
                                        );
                                        
                                        VariableDeclarationExpr loggerClassVar = new VariableDeclarationExpr(
                                            StaticJavaParser.parseClassOrInterfaceType("Class<?>"),
                                            "loggerClass"
                                        );
                                        AssignmentExpr assignLoggerClass = new AssignmentExpr(
                                            new NameExpr("loggerClass"),
                                            forNameCall1,
                                            AssignmentExpr.Operator.ASSIGN
                                        );
                                        
                                        VariableDeclarationExpr setLevelMethodVar = new VariableDeclarationExpr(
                                            StaticJavaParser.parseClassOrInterfaceType("java.lang.reflect.Method"),
                                            "setLevelMethod"
                                        );
                                        AssignmentExpr assignSetLevelMethod = new AssignmentExpr(
                                            new NameExpr("setLevelMethod"),
                                            getMethodCall,
                                            AssignmentExpr.Operator.ASSIGN
                                        );
                                        
                                        // Create the try block
                                        BlockStmt tryBlock = new BlockStmt();
                                        tryBlock.addStatement(new ExpressionStmt(assignLogger));
                                        tryBlock.addStatement(new ExpressionStmt(assignLoggerClass));
                                        tryBlock.addStatement(new ExpressionStmt(assignSetLevelMethod));
                                        tryBlock.addStatement(new ExpressionStmt(invokeCall));
                                        
                                        // Create catch block
                                        BlockStmt catchBlock = new BlockStmt();
                                        MethodCallExpr throwNew = new MethodCallExpr(
                                            new NameExpr("RuntimeException"),
                                            "RuntimeException"
                                        );
                                        throwNew.addArgument(new NameExpr("e"));
                                        catchBlock.addStatement(new ThrowStmt(throwNew));
                                        
                                        CatchClause catchClause = new CatchClause(
                                            StaticJavaParser.parseParameter("Exception e"),
                                            catchBlock
                                        );
                                        
                                        TryStmt tryStmt = new TryStmt();
                                        tryStmt.setTryBlock(tryBlock);
                                        tryStmt.setCatchClauses(new NodeList<>(catchClause));
                                        
                                        return tryStmt;
                                    }
                                }
                            }
                        }
                    }
                    return super.visit(n, arg);
                }
            }, null) != null;
            
            if (modified) {
                transformedFiles++;
                Path outputFile = outputDir.resolve(sourceDir.relativize(javaFile));
                Files.createDirectories(outputFile.getParent());
                Files.write(outputFile, printer.print(cu).getBytes());
            } else {
                // Copy file unchanged
                Path outputFile = outputDir.resolve(sourceDir.relativize(javaFile));
                Files.createDirectories(outputFile.getParent());
                Files.copy(javaFile, outputFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        
        System.out.println("Transformation complete:");
        System.out.println("  Files processed: " + javaFiles.size());
        System.out.println("  Files transformed: " + transformedFiles);
        System.out.println("  Logger setLevel patterns replaced with reflection: " + transformedPatterns[0]);
        System.out.println("\nNote: This transformation uses reflection to avoid compile-time dependency");
        System.out.println("on ch.qos.logback.classic.Logger which implements org.slf4j.spi.LoggingEventAware.");
        System.out.println("The LoggingEventAware interface does not exist in SLF4J 1.7.36,");
        System.out.println("causing compilation errors with logback-classic 1.4.4.");
    }
}