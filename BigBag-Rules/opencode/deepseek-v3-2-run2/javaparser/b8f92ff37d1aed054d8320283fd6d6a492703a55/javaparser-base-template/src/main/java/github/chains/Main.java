package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        List<Path> javaFiles = Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        JavaParser parser = new JavaParser();
        int modifiedFiles = 0;
        
        for (Path javaFile : javaFiles) {
            try (FileInputStream in = new FileInputStream(javaFile.toFile())) {
                CompilationUnit cu = parser.parse(in).getResult().orElse(null);
                if (cu == null) {
                    continue;
                }
                
                boolean[] modified = {false};
                cu.accept(new ModifierVisitor<Void>() {
                    @Override
                    public Visitable visit(MethodCallExpr n, Void arg) {
                        // Check if this is a call to TestListResolver.getWildcard()
                        if (n.getNameAsString().equals("getWildcard")) {
                            boolean isTestListResolverCall = false;
                            
                            if (n.getScope().isPresent()) {
                                String scope = n.getScope().get().toString();
                                // Match TestListResolver.getWildcard() or fully qualified
                                if (scope.equals("TestListResolver") || 
                                    scope.equals("org.apache.maven.surefire.api.testset.TestListResolver")) {
                                    isTestListResolverCall = true;
                                }
                            } else {
                                // Could be a static import: getWildcard()
                                // We need to check imports to be sure, but for simplicity
                                // we'll replace any getWildcard() call
                                isTestListResolverCall = true;
                            }
                            
                            if (isTestListResolverCall) {
                                // Replace with new TestListResolver((String) null)
                                ObjectCreationExpr newExpr = new ObjectCreationExpr();
                                newExpr.setType("org.apache.maven.surefire.api.testset.TestListResolver");
                                // Create a cast expression: (String) null
                                CastExpr castExpr = new CastExpr();
                                castExpr.setType("String");
                                castExpr.setExpression(new NullLiteralExpr());
                                newExpr.addArgument(castExpr);
                                modified[0] = true;
                                return newExpr;
                            }
                        }
                        return super.visit(n, arg);
                    }
                }, null);
                
                if (modified[0]) {
                    Files.write(javaFile, cu.toString().getBytes());
                    modifiedFiles++;
                    System.out.println("Modified: " + javaFile);
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("Modified " + modifiedFiles + " files");
    }
}