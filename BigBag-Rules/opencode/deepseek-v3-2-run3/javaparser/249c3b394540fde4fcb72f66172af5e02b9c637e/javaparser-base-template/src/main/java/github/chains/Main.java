package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.SuperExpr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for fixing breaking changes in dependency updates.
 * This transformation removes specified exceptions from method throws clauses
 * when methods override or call parent methods that no longer declare those exceptions.
 * 
 * Usage: java github.chains.Main <source-directory> [method-name] [exception-class...]
 * Example: java github.chains.Main ./src org.yaml.snakeyaml.representer.Representer.getProperties IntrospectionException
 */
public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("Usage: java github.chains.Main <source-directory> <method-name> <exception-class>...");
            System.err.println("Example: java github.chains.Main ./src getProperties IntrospectionException");
            System.err.println("Example: java github.chains.Main ./src \"org.yaml.snakeyaml.representer.Representer.getProperties\" IntrospectionException");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        String targetMethodName = args[1];
        // Extract simple method name if fully qualified
        String simpleMethodName = targetMethodName;
        if (targetMethodName.contains(".")) {
            String[] parts = targetMethodName.split("\\.");
            simpleMethodName = parts[parts.length - 1];
        }
        
        Set<String> exceptionsToRemove = new HashSet<>(Arrays.asList(Arrays.copyOfRange(args, 2, args.length)));
        
        JavaParser javaParser = new JavaParser();
        
        // Walk through all Java files
        List<Path> javaFiles = Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        int modifiedFiles = 0;
        int modifiedMethods = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = javaParser.parse(javaFile).getResult().orElse(null);
                if (cu == null) {
                    continue;
                }
                
                boolean fileModified = false;
                
                // Find all method declarations
                List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
                
                for (MethodDeclaration method : methods) {
                    // Check if method has a throws clause with any of the target exceptions
                    boolean hasTargetException = method.getThrownExceptions().stream()
                        .anyMatch(exception -> {
                            String exceptionName = exception.toString();
                            // Check both simple and fully qualified names
                            for (String exceptionToRemove : exceptionsToRemove) {
                                if (exceptionName.equals(exceptionToRemove) ||
                                    exceptionName.endsWith("." + exceptionToRemove)) {
                                    return true;
                                }
                            }
                            return false;
                        });
                    
                    if (hasTargetException) {
                        // Check if this method is relevant for transformation:
                        // 1. Method name matches target method name (could be override)
                        // 2. OR method calls super.<target-method>()
                        boolean methodNameMatches = method.getNameAsString().equals(simpleMethodName);
                        final String finalSimpleMethodName = simpleMethodName;
                        boolean callsSuperTargetMethod = method.findAll(MethodCallExpr.class).stream()
                            .anyMatch(mce -> {
                                if (mce.getScope().isPresent() && mce.getScope().get() instanceof SuperExpr) {
                                    String calledMethodName = mce.getNameAsString();
                                    return calledMethodName.equals(finalSimpleMethodName);
                                }
                                return false;
                            });
                        
                        if (methodNameMatches || callsSuperTargetMethod) {
                            // Remove target exceptions from throws clause
                            NodeList<ReferenceType> newExceptions = new NodeList<>();
                            for (ReferenceType exception : method.getThrownExceptions()) {
                                String exceptionName = exception.toString();
                                boolean shouldKeep = true;
                                
                                for (String exceptionToRemove : exceptionsToRemove) {
                                    if (exceptionName.equals(exceptionToRemove) ||
                                        exceptionName.endsWith("." + exceptionToRemove)) {
                                        shouldKeep = false;
                                        break;
                                    }
                                }
                                
                                if (shouldKeep) {
                                    newExceptions.add(exception);
                                }
                            }
                            
                            method.setThrownExceptions(newExceptions);
                            fileModified = true;
                            modifiedMethods++;
                            
                            System.out.println("Fixed method in " + javaFile + ": " + method.getSignature());
                        }
                    }
                }
                
                if (fileModified) {
                    // Write back the modified file
                    Files.write(javaFile, cu.toString().getBytes());
                    modifiedFiles++;
                }
                
            } catch (Exception e) {
                System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("Modified " + modifiedMethods + " methods in " + modifiedFiles + " files");
        System.out.println("Transformation complete for method: " + targetMethodName);
        System.out.println("Removed exceptions: " + String.join(", ", exceptionsToRemove));
    }
}