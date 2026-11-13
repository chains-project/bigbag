package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
    
    // Configuration for type replacements
    private static class TypeReplacement {
        final String oldFqn;
        final String newFqn;
        final boolean removeTypeArguments;
        
        TypeReplacement(String oldFqn, String newFqn, boolean removeTypeArguments) {
            this.oldFqn = oldFqn;
            this.newFqn = newFqn;
            this.removeTypeArguments = removeTypeArguments;
        }
    }
    
    // Configuration for method replacements  
    private static class MethodReplacement {
        final String className;
        final String oldMethodName;
        final String newMethodName;
        final Expression[] additionalArgs;
        
        MethodReplacement(String className, String oldMethodName, String newMethodName, Expression[] additionalArgs) {
            this.className = className;
            this.oldMethodName = oldMethodName;
            this.newMethodName = newMethodName;
            this.additionalArgs = additionalArgs;
        }
    }
    
    private static final TypeReplacement[] TYPE_REPLACEMENTS = {
        // Billy-core API changes
        new TypeReplacement("com.premiumminds.billy.core.services.StringID", 
                           "com.premiumminds.billy.core.services.UID", true),
        new TypeReplacement("com.premiumminds.billy.core.services.entities.Entity",
                           "com.premiumminds.billy.core.services.entities.Entity", true),
        // Note: DAO replacement is more complex - handled separately
    };
    
    private static final MethodReplacement[] METHOD_REPLACEMENTS = {
        // StringID.fromValue(String) -> UID.fromString(String)
        new MethodReplacement("StringID", "fromValue", "fromString", null),
        // FopFactory.newInstance() -> FopFactory.newInstance(new URI("."))
        new MethodReplacement("FopFactory", "newInstance", "newInstance", 
            new Expression[] {
                new ObjectCreationExpr(null, new ClassOrInterfaceType(null, "java.net.URI"),
                    new NodeList<>(new com.github.javaparser.ast.expr.StringLiteralExpr(".")))
            })
    };
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        System.out.println("Processing source directory: " + sourceDir);
        
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(javaFiles::add);
            
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        JavaParser javaParser = new JavaParser();
        int modifiedFiles = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = javaParser.parse(javaFile).getResult().orElse(null);
                if (cu == null) continue;
                
                final boolean[] modified = new boolean[]{false};
                
                // 1. Fix imports based on type replacements
                for (ImportDeclaration importDecl : cu.getImports()) {
                    String importName = importDecl.getNameAsString();
                    for (TypeReplacement replacement : TYPE_REPLACEMENTS) {
                        if (importName.equals(replacement.oldFqn)) {
                            importDecl.setName(replacement.newFqn);
                            modified[0] = true;
                            break;
                        }
                    }
                }
                
                // 2. Visitor to fix type references and method calls
                ModifierVisitor<Void> visitor = new ModifierVisitor<Void>() {
                    private String getSimpleName(String fqn) {
                        int lastDot = fqn.lastIndexOf('.');
                        return lastDot >= 0 ? fqn.substring(lastDot + 1) : fqn;
                    }
                    
                    @Override
                    public Visitable visit(ClassOrInterfaceType n, Void arg) {
                        String typeName = n.getNameAsString();
                        
                        // Apply type replacements
                        for (TypeReplacement replacement : TYPE_REPLACEMENTS) {
                            String simpleOldName = getSimpleName(replacement.oldFqn);
                            String simpleNewName = getSimpleName(replacement.newFqn);
                            
                            if (typeName.equals(simpleOldName)) {
                                n.setName(simpleNewName);
                                if (replacement.removeTypeArguments) {
                                    // Clear type arguments by removing them
                                    // This should remove the angle brackets entirely
                                    n.removeTypeArguments();
                                }
                                modified[0] = true;
                            }
                        }
                        
                        // Special handling for DAO<TID, TInterface> -> DAO<T>
                        if (typeName.equals("DAO") && n.getTypeArguments().isPresent()) {
                            NodeList<Type> typeArgs = n.getTypeArguments().get();
                            if (typeArgs.size() == 2) {
                                // Keep only the first type argument
                                NodeList<Type> newTypeArgs = new NodeList<>();
                                newTypeArgs.add(typeArgs.get(0));
                                n.setTypeArguments(newTypeArgs);
                                modified[0] = true;
                            }
                        }
                        
                        return super.visit(n, arg);
                    }
                    
                    @Override
                    public Visitable visit(MethodCallExpr n, Void arg) {
                        String methodName = n.getNameAsString();
                        
                        // Apply method replacements
                        for (MethodReplacement replacement : METHOD_REPLACEMENTS) {
                            if (methodName.equals(replacement.oldMethodName)) {
                                Optional<Expression> scope = n.getScope();
                                if (scope.isPresent() && scope.get() instanceof NameExpr) {
                                    NameExpr nameExpr = (NameExpr) scope.get();
                                    if (nameExpr.getNameAsString().equals(replacement.className)) {
                                        n.setName(replacement.newMethodName);
                                        
                                        // Update class name if it matches a type replacement
                                        for (TypeReplacement typeReplacement : TYPE_REPLACEMENTS) {
                                            String simpleOldName = getSimpleName(typeReplacement.oldFqn);
                                            String simpleNewName = getSimpleName(typeReplacement.newFqn);
                                            if (nameExpr.getNameAsString().equals(simpleOldName)) {
                                                nameExpr.setName(simpleNewName);
                                                break;
                                            }
                                        }
                                        
                                        // Add additional arguments if needed
                                        if (replacement.additionalArgs != null && replacement.additionalArgs.length > 0) {
                                            NodeList<Expression> newArgs = new NodeList<>();
                                            newArgs.addAll(n.getArguments());
                                            for (Expression additionalArg : replacement.additionalArgs) {
                                                newArgs.add(additionalArg.clone());
                                            }
                                            n.setArguments(newArgs);
                                        }
                                        
                                        modified[0] = true;
                                    }
                                }
                            }
                        }
                        
                        return super.visit(n, arg);
                    }
                };
                
                cu.accept(visitor, null);
                
                if (modified[0]) {
                    Files.write(javaFile, cu.toString().getBytes());
                    modifiedFiles++;
                    System.out.println("Modified: " + javaFile);
                }
                
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("Modified " + modifiedFiles + " files");
    }
}