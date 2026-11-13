package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.err.println("Transformation rules for PubNub 6.3.2 breaking changes:");
            System.err.println("  1. PNConfiguration() -> PNConfiguration(\"\")");
            System.err.println("  2. Remove imports and references to PNMembershipResult, PNSpaceResult, PNUserResult");
            System.err.println("  3. Add file() method to SubscribeCallback implementations");
            System.err.println("  4. Remove obsolete callback methods (user, space, membership)");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<File> javaFiles = Files.walk(Paths.get(sourceDir))
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .map(Path::toFile)
                .collect(Collectors.toList());
            
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            JavaParser javaParser = new JavaParser();
            int modifiedFiles = 0;
            
            for (File file : javaFiles) {
                try {
                    CompilationUnit cu = javaParser.parse(file).getResult().orElse(null);
                    if (cu == null) continue;
                    
                    PubNubTransformationVisitor visitor = new PubNubTransformationVisitor();
                    cu.accept(visitor, null);
                    
                    if (visitor.isModified()) {
                        Files.write(file.toPath(), cu.toString().getBytes());
                        modifiedFiles++;
                        System.out.println("Modified: " + file.getPath());
                    }
                } catch (Exception e) {
                    System.err.println("Error processing " + file.getPath() + ": " + e.getMessage());
                }
            }
            
            System.out.println("Modified " + modifiedFiles + " files");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    static class PubNubTransformationVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        private final Set<String> removedClasses = new HashSet<>(Arrays.asList(
            "PNMembershipResult", "PNSpaceResult", "PNUserResult"
        ));
        
        public boolean isModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Fix PNConfiguration constructor calls
            String typeName = n.getType().asString();
            if (typeName.equals("PNConfiguration")) {
                NodeList<Expression> arguments = n.getArguments();
                if (arguments.isEmpty()) {
                    // Old pattern: new PNConfiguration()
                    // New pattern: new PNConfiguration("") 
                    n.setArguments(NodeList.nodeList(new StringLiteralExpr("")));
                    modified = true;
                    System.out.println("Transformed PNConfiguration() -> PNConfiguration(\"\")");
                }
            }
            
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ClassOrInterfaceDeclaration n, Void arg) {
            // First visit children to collect information
            super.visit(n, arg);
            
            // Check if class or anonymous class implements SubscribeCallback
            boolean implementsSubscribeCallback = n.getImplementedTypes().stream()
                .anyMatch(t -> t.getNameAsString().equals("SubscribeCallback"));
            
            if (implementsSubscribeCallback) {
                // Add missing file() method if not present
                if (!hasFileMethod(n)) {
                    addFileMethod(n);
                    modified = true;
                }
                
                // Remove obsolete callback methods (user, space, membership)
                List<MethodDeclaration> methodsToRemove = new ArrayList<>();
                for (MethodDeclaration method : n.getMethods()) {
                    String methodName = method.getNameAsString();
                    if (methodName.equals("user") || methodName.equals("space") || methodName.equals("membership")) {
                        methodsToRemove.add(method);
                    }
                }
                
                if (!methodsToRemove.isEmpty()) {
                    n.getMembers().removeAll(methodsToRemove);
                    modified = true;
                    System.out.println("Removed obsolete callback methods: " + 
                        methodsToRemove.stream().map(m -> m.getNameAsString()).collect(Collectors.toList()));
                }
            }
            
            return n;
        }
        
        @Override
        public Visitable visit(CompilationUnit cu, Void arg) {
            super.visit(cu, arg);
            
            // Remove imports for removed classes
            List<com.github.javaparser.ast.ImportDeclaration> importsToRemove = cu.getImports().stream()
                .filter(imp -> {
                    String importName = imp.getNameAsString();
                    return importName.endsWith(".PNMembershipResult") ||
                           importName.endsWith(".PNSpaceResult") ||
                           importName.endsWith(".PNUserResult");
                })
                .collect(Collectors.toList());
            
            if (!importsToRemove.isEmpty()) {
                cu.getImports().removeAll(importsToRemove);
                modified = true;
                System.out.println("Removed imports for removed classes: " + 
                    importsToRemove.stream().map(imp -> imp.getNameAsString()).collect(Collectors.toList()));
            }
            
            return cu;
        }
        
        @Override
        public Visitable visit(MethodDeclaration n, Void arg) {
            // Check if method uses removed class types in parameters
            boolean hasRemovedParameterType = n.getParameters().stream()
                .anyMatch(p -> removedClasses.contains(p.getType().asString()));
            
            if (hasRemovedParameterType) {
                // This method should be removed since it's implementing a removed interface method
                // But we'll handle removal in the class visitor
                return n;
            }
            
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(Parameter n, Void arg) {
            String typeName = n.getType().asString();
            if (removedClasses.contains(typeName)) {
                // Replace with Object type for safety
                n.setType(new ClassOrInterfaceType(null, "Object"));
                modified = true;
                System.out.println("Replaced parameter type " + typeName + " with Object");
            }
            return super.visit(n, arg);
        }
        
        private boolean hasFileMethod(ClassOrInterfaceDeclaration clazz) {
            return clazz.getMethods().stream()
                .anyMatch(m -> m.getNameAsString().equals("file"));
        }
        
        private void addFileMethod(ClassOrInterfaceDeclaration clazz) {
            MethodDeclaration fileMethod = new MethodDeclaration();
            fileMethod.setName("file");
            fileMethod.setType("void");
            fileMethod.addAnnotation("Override");
            fileMethod.setModifiers(com.github.javaparser.ast.Modifier.Keyword.PUBLIC);
            
            // Add parameters: PubNub pubnub, PNFileEventResult pnFileEventResult
            fileMethod.addParameter("com.pubnub.api.PubNub", "pubnub");
            fileMethod.addParameter("com.pubnub.api.models.consumer.pubsub.files.PNFileEventResult", "pnFileEventResult");
            
            // Add simple logging body
            BlockStmt body = new BlockStmt();
            body.addStatement("// Handle file events");
            body.addStatement("System.out.println(\"File event received\");");
            fileMethod.setBody(body);
            
            clazz.addMember(fileMethod);
            System.out.println("Added missing file() method to SubscribeCallback implementation");
        }
    }
}