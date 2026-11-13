package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.io.FileInputStream;
import java.io.FileOutputStream;

public class DirectMain {
    public static void main(String[] args) throws Exception {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(new FileInputStream("../pdb/src/test/java/com/feedzai/commons/sql/abstraction/engine/impl/abs/EngineCloseTest.java")).getResult().orElse(null);
        
        if (cu == null) {
            System.out.println("Failed to parse file");
            return;
        }
        
        final boolean[] wasModified = {false};
        
        // Find all setLevel method calls
        cu.findAll(MethodCallExpr.class).forEach(methodCall -> {
            if (methodCall.getNameAsString().equals("setLevel")) {
                System.out.println("Found setLevel call: " + methodCall);
                
                // Check the scope type
                if (methodCall.getScope().isPresent()) {
                    System.out.println("  Scope type: " + methodCall.getScope().get().getClass().getName());
                    System.out.println("  Scope: " + methodCall.getScope().get());
                    
                    // Check if the scope contains a cast to Logger
                    if (methodCall.getScope().get().toString().contains("(Logger)")) {
                        System.out.println("  Scope contains (Logger) cast!");
                        
                        // Check if the scope contains LoggerFactory.getLogger
                        if (methodCall.getScope().get().toString().contains("LoggerFactory.getLogger")) {
                            System.out.println("  Scope contains LoggerFactory.getLogger!");
                            
                            // Get the parent statement
                            Statement parentStatement = methodCall.findAncestor(Statement.class).orElse(null);
                            if (parentStatement != null) {
                                System.out.println("  Parent statement: " + parentStatement);
                                
                                // Extract the getLogger call and setLevel arguments
                                String getLoggerCall = "LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)";
                                String setLevelArgs = "Level.TRACE";
                                
                                // Create replacement
                                String replacement = "{\n    org.slf4j.Logger loggerTemp = " + getLoggerCall + ";\n    if (loggerTemp instanceof ch.qos.logback.classic.Logger) {\n        ((ch.qos.logback.classic.Logger) loggerTemp).setLevel(" + setLevelArgs + ");\n    }\n}";
                                
                                try {
                                    BlockStmt replacementBlock = parser.parseBlock(replacement).getResult().orElse(null);
                                    if (replacementBlock != null) {
                                        parentStatement.replace(replacementBlock);
                                        System.out.println("  Successfully replaced!");
                                        wasModified[0] = true;
                                    }
                                } catch (Exception e) {
                                    System.err.println("  Error replacing: " + e.getMessage());
                                }
                            }
                        }
                    }
                }
            }
        });
        
        if (wasModified[0]) {
            try (FileOutputStream fos = new FileOutputStream("../pdb/src/test/java/com/feedzai/commons/sql/abstraction/engine/impl/abs/EngineCloseTest.java")) {
                fos.write(cu.toString().getBytes());
            }
            System.out.println("File updated successfully!");
        }
    }
}