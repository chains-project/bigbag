package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transform.jar <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        transformDirectory(sourceDir);
    }
    
    private static void transformDirectory(Path sourceDir) throws IOException {
        try (Stream<Path> paths = Files.walk(sourceDir)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(Main::transformFile);
        }
    }
    
    private static void transformFile(Path file) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(file).getResult().orElseThrow();
            
            // Track imports to add
            List<String> importsToAdd = new ArrayList<>();
            
            // Apply transformations
            cu.accept(new ClientHelperConstructorTransformer(importsToAdd), null);
            cu.accept(new StringUtilsIsAllBlankTransformer(importsToAdd), null);
            
            // Add required imports
            for (String importStr : importsToAdd) {
                if (cu.getImports().stream().noneMatch(imp -> imp.getNameAsString().equals(importStr))) {
                    cu.addImport(importStr);
                }
            }
            
            // Write back transformed file
            Files.write(file, cu.toString().getBytes());
            System.out.println("Transformed: " + file);
        } catch (IOException e) {
            System.err.println("Error transforming " + file + ": " + e.getMessage());
        }
    }
    
    /**
     * Transformation for ClientHelper constructor changes
     * Old: new ClientHelper(String credentials, TaskListener listener, String client, String charset)
     * New: new ClientHelper(P4BaseCredentials credentials, TaskListener listener, Workspace workspace)
     * Transform to: new ClientHelper(ConnectionHelper.findCredential(credentials), listener, 
     *                 new StaticWorkspaceImpl(client, false, charset))
     */
    private static class ClientHelperConstructorTransformer extends ModifierVisitor<Void> {
        private final List<String> importsToAdd;
        
        public ClientHelperConstructorTransformer(List<String> importsToAdd) {
            this.importsToAdd = importsToAdd;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a ClientHelper constructor call
            if (n.getTypeAsString().equals("ClientHelper")) {
                // Check if it matches old pattern: (String, TaskListener, String, String)
                if (n.getArguments().size() == 4) {
                    importsToAdd.add("org.jenkinsci.plugins.p4.client.ConnectionHelper");
                    importsToAdd.add("org.jenkinsci.plugins.p4.workspace.StaticWorkspaceImpl");
                    
                    // Get arguments
                    String credentialsArg = n.getArgument(0).toString();
                    String listenerArg = n.getArgument(1).toString();
                    String clientArg = n.getArgument(2).toString();
                    String charsetArg = n.getArgument(3).toString();
                    
                    // Build replacement: new ClientHelper(ConnectionHelper.findCredential(credentialsArg), 
                    //                    listenerArg, new StaticWorkspaceImpl(clientArg, false, charsetArg))
                    String replacement = String.format(
                        "new ClientHelper(ConnectionHelper.findCredential(%s), %s, new StaticWorkspaceImpl(%s, false, %s))",
                        credentialsArg, listenerArg, clientArg, charsetArg
                    );
                    
                    // Replace the constructor call
                    JavaParser javaParser = new JavaParser();
                    return javaParser.parseExpression(replacement).getResult().orElseThrow();
                }
            }
            return super.visit(n, arg);
        }
    }
    
    /**
     * Transformation for StringUtils.isAllBlank removal
     * Replace StringUtils.isAllBlank(args...) with Stream.of(args).allMatch(StringUtils::isBlank)
     */
    private static class StringUtilsIsAllBlankTransformer extends ModifierVisitor<Void> {
        private final List<String> importsToAdd;
        
        public StringUtilsIsAllBlankTransformer(List<String> importsToAdd) {
            this.importsToAdd = importsToAdd;
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check if this is a call to isAllBlank
            if (n.getNameAsString().equals("isAllBlank")) {
                // Check if it's StringUtils.isAllBlank or static imported isAllBlank
                boolean isStringUtilsCall = n.getScope()
                    .map(scope -> scope.toString().equals("StringUtils"))
                    .orElse(false);
                
                if (isStringUtilsCall || n.getScope().isEmpty()) {
                    importsToAdd.add("java.util.stream.Stream");
                    
                    // Build replacement: Stream.of(args).allMatch(StringUtils::isBlank)
                    StringBuilder replacement = new StringBuilder("Stream.of(");
                    
                    for (int i = 0; i < n.getArguments().size(); i++) {
                        if (i > 0) replacement.append(", ");
                        replacement.append(n.getArgument(i));
                    }
                    replacement.append(").allMatch(StringUtils::isBlank)");
                    
                    // Replace the method call
                    try {
                        JavaParser javaParser = new JavaParser();
                        return javaParser.parseExpression(replacement.toString()).getResult().orElseThrow();
                    } catch (Exception e) {
                        System.err.println("Failed to parse replacement: " + replacement);
                        return super.visit(n, arg);
                    }
                }
            }
            return super.visit(n, arg);
        }
    }
}