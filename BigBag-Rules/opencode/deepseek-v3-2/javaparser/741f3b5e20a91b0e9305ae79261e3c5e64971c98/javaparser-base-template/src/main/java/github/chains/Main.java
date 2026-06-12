package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = findJavaFiles(Paths.get(sourceDir));
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int modifiedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (processFile(javaFile)) {
                    modifiedFiles++;
                }
            }
            
            System.out.println("Modified " + modifiedFiles + " files");
        } catch (Exception e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(Path dir) throws Exception {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        return javaFiles;
    }
    
    private static boolean processFile(Path filePath) throws Exception {
        JavaParser javaParser = new JavaParser();
        CompilationUnit cu = javaParser.parse(filePath).getResult().orElseThrow();
        
        boolean[] modified = {false};
        
        // Visitor to find ObjectMapper.readValue calls and wrap them in try-catch
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr n, Void arg) {
                super.visit(n, arg);
                
                // Check if this is a readValue method call
                if (n.getNameAsString().equals("readValue")) {
                    // Check if it's likely an ObjectMapper call
                    if (n.getScope().isPresent()) {
                        String scope = n.getScope().get().toString();
                        if (scope.contains("objectMapper") || scope.contains("ObjectMapper") || 
                            scope.contains("mapper") || scope.contains("Mapper")) {
                            
                            // Check if already in try-catch
                            if (!n.findAncestor(TryStmt.class).isPresent()) {
                                // Create a try-catch block
                                TryStmt tryStmt = new TryStmt();
                                
                                // Create try block with the method call
                                BlockStmt tryBlock = new BlockStmt();
                                tryBlock.addStatement(new ExpressionStmt(n.clone()));
                                tryStmt.setTryBlock(tryBlock);
                                
                                // Create catch clause for IOException
                                CatchClause catchClause = new CatchClause();
                                ClassOrInterfaceType ioExceptionType = new ClassOrInterfaceType(null, "IOException");
                                com.github.javaparser.ast.body.Parameter param = 
                                    new com.github.javaparser.ast.body.Parameter(ioExceptionType, "e");
                                catchClause.setParameter(param);
                                
                                // Create catch block - for now just rethrow
                                BlockStmt catchBlock = new BlockStmt();
                                catchBlock.addStatement("throw e;");
                                catchClause.setBody(catchBlock);
                                
                                // Set catch clauses
                                tryStmt.setCatchClauses(new NodeList<>(catchClause));
                                
                                // Replace the method call with try-catch
                                n.replace(tryStmt);
                                modified[0] = true;
                                
                                // Ensure IOException is imported
                                cu.addImport("java.io.IOException");
                            }
                        }
                    }
                }
            }
        }, null);
        
        if (modified[0]) {
            // Write back the modified file
            Files.write(filePath, cu.toString().getBytes());
            System.out.println("Modified: " + filePath);
            return true;
        }
        
        return false;
    }
}