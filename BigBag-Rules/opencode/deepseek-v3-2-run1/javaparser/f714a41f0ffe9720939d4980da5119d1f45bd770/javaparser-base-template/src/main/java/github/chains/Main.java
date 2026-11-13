package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -cp target/classes github.chains.Main <source_directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        System.out.println("Processing source directory: " + sourceDir.toAbsolutePath());
        
        List<Path> javaFiles = Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
            
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int totalChanges = 0;
        JavaParser parser = new JavaParser();
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
                if (cu == null) continue;
                
                PubNubTransformer transformer = new PubNubTransformer();
                cu.accept(transformer, null);
                
                int changes = transformer.getChangesCount();
                if (changes > 0) {
                    Files.write(javaFile, cu.toString().getBytes());
                    System.out.println("Modified " + javaFile + " (" + changes + " changes)");
                    totalChanges += changes;
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("Total changes made: " + totalChanges);
    }
    
    static class PubNubTransformer extends ModifierVisitor<Void> {
        private int changesCount = 0;
        
        public int getChangesCount() {
            return changesCount;
        }
        
        @Override
        public Node visit(ObjectCreationExpr n, Void arg) {
            ObjectCreationExpr expr = (ObjectCreationExpr) super.visit(n, arg);
            
            if (expr.getType().asString().equals("PNConfiguration")) {
                if (expr.getArguments().isEmpty()) {
                    com.github.javaparser.ast.expr.ObjectCreationExpr userIdExpr = 
                        new com.github.javaparser.ast.expr.ObjectCreationExpr();
                    userIdExpr.setType(new ClassOrInterfaceType(null, "UserId"));
                    userIdExpr.getArguments().add(new com.github.javaparser.ast.expr.StringLiteralExpr(""));
                    expr.getArguments().add(userIdExpr);
                    changesCount++;
                    System.out.println("  Fixed PNConfiguration constructor - added UserId parameter");
                    
                    addUserIdImportIfMissing(expr);
                    addPubNubExceptionImportIfMissing(expr);
                }
            }
            
            if (expr.getType().asString().equals("SubscribeCallback") && expr.getAnonymousClassBody().isPresent()) {
                handleAnonymousSubscribeCallback(expr);
            }
            
            return expr;
        }
        
        private void handleAnonymousSubscribeCallback(ObjectCreationExpr expr) {
            ClassOrInterfaceType baseType = new ClassOrInterfaceType(null, "SubscribeCallback.BaseSubscribeCallback");
            expr.setType(baseType);
            changesCount++;
            System.out.println("  Changed anonymous SubscribeCallback to BaseSubscribeCallback");
            
            addFileMethodToAnonymousClass(expr);
        }
        
        private void addFileMethodToAnonymousClass(ObjectCreationExpr expr) {
            if (!expr.getAnonymousClassBody().isPresent()) return;
            
            NodeList<BodyDeclaration<?>> members = expr.getAnonymousClassBody().get();
            boolean hasFileMethod = members.stream()
                .filter(m -> m instanceof MethodDeclaration)
                .map(m -> (MethodDeclaration) m)
                .anyMatch(m -> m.getNameAsString().equals("file"));
                
            if (!hasFileMethod) {
                MethodDeclaration fileMethod = new MethodDeclaration();
                fileMethod.setPublic(true);
                fileMethod.addAnnotation("Override");
                fileMethod.setName("file");
                fileMethod.addParameter("PubNub", "pubnub");
                fileMethod.addParameter("PNFileEventResult", "pnFileEventResult");
                fileMethod.setType("void");
                fileMethod.setBody(new BlockStmt());
                members.add(fileMethod);
                changesCount++;
                System.out.println("  Added empty file() method to anonymous class");
                
                addFileImportToCompilationUnit(expr);
            }
        }
        
        @Override
        public Node visit(ClassOrInterfaceDeclaration n, Void arg) {
            ClassOrInterfaceDeclaration decl = (ClassOrInterfaceDeclaration) super.visit(n, arg);
            
            List<ClassOrInterfaceType> extendsList = new ArrayList<>(decl.getExtendedTypes());
            boolean changedToBaseCallback = false;
            for (int i = 0; i < extendsList.size(); i++) {
                ClassOrInterfaceType type = extendsList.get(i);
                if (type.getNameAsString().equals("SubscribeCallback")) {
                    ClassOrInterfaceType baseType = new ClassOrInterfaceType(null, "SubscribeCallback.BaseSubscribeCallback");
                    extendsList.set(i, baseType);
                    changesCount++;
                    changedToBaseCallback = true;
                    System.out.println("  Changed SubscribeCallback to BaseSubscribeCallback");
                }
            }
            
            decl.getExtendedTypes().clear();
            decl.getExtendedTypes().addAll(extendsList);
            
            if (changedToBaseCallback) {
                addFileMethodIfMissing(decl);
            }
            
            return decl;
        }
        
        private void addFileMethodIfMissing(ClassOrInterfaceDeclaration decl) {
            boolean hasFileMethod = decl.getMethods().stream()
                .anyMatch(m -> m.getNameAsString().equals("file"));
                
            if (!hasFileMethod) {
                MethodDeclaration fileMethod = new MethodDeclaration();
                fileMethod.setPublic(true);
                fileMethod.addAnnotation("Override");
                fileMethod.setName("file");
                fileMethod.addParameter("PubNub", "pubnub");
                fileMethod.addParameter("PNFileEventResult", "pnFileEventResult");
                fileMethod.setType("void");
                fileMethod.setBody(new BlockStmt());
                decl.addMember(fileMethod);
                changesCount++;
                System.out.println("  Added empty file() method implementation");
                
                addFileImportIfMissing(decl);
            }
        }
        
        private void addFileImportIfMissing(ClassOrInterfaceDeclaration decl) {
            decl.findCompilationUnit().ifPresent(cu -> {
                boolean hasFileImport = cu.getImports().stream()
                    .anyMatch(imp -> imp.getNameAsString().equals("com.pubnub.api.models.consumer.pubsub.files.PNFileEventResult"));
                    
                if (!hasFileImport) {
                    cu.addImport("com.pubnub.api.models.consumer.pubsub.files.PNFileEventResult");
                    changesCount++;
                    System.out.println("  Added import for PNFileEventResult");
                }
            });
        }
        
        private void addFileImportToCompilationUnit(Node node) {
            node.findCompilationUnit().ifPresent(cu -> {
                boolean hasFileImport = cu.getImports().stream()
                    .anyMatch(imp -> imp.getNameAsString().equals("com.pubnub.api.models.consumer.pubsub.files.PNFileEventResult"));
                    
                if (!hasFileImport) {
                    cu.addImport("com.pubnub.api.models.consumer.pubsub.files.PNFileEventResult");
                    changesCount++;
                    System.out.println("  Added import for PNFileEventResult");
                }
            });
        }
        
        private void addUserIdImportIfMissing(Node node) {
            node.findCompilationUnit().ifPresent(cu -> {
                boolean hasUserIdImport = cu.getImports().stream()
                    .anyMatch(imp -> imp.getNameAsString().equals("com.pubnub.api.UserId"));
                    
                if (!hasUserIdImport) {
                    cu.addImport("com.pubnub.api.UserId");
                    changesCount++;
                    System.out.println("  Added import for UserId");
                }
            });
        }
        
        private void addPubNubExceptionImportIfMissing(Node node) {
            node.findCompilationUnit().ifPresent(cu -> {
                boolean hasPubNubExceptionImport = cu.getImports().stream()
                    .anyMatch(imp -> imp.getNameAsString().equals("com.pubnub.api.PubNubException"));
                    
                if (!hasPubNubExceptionImport) {
                    cu.addImport("com.pubnub.api.PubNubException");
                    changesCount++;
                    System.out.println("  Added import for PubNubException");
                }
            });
        }
        
        @Override
        public Node visit(MethodDeclaration n, Void arg) {
            MethodDeclaration method = (MethodDeclaration) super.visit(n, arg);
            
            String methodName = method.getNameAsString();
            if (methodName.equals("user") || methodName.equals("space") || methodName.equals("membership")) {
                List<String> paramTypes = method.getParameters().stream()
                    .map(p -> p.getType().asString())
                    .collect(Collectors.toList());
                    
                if (paramTypes.contains("PNUserResult") || paramTypes.contains("PNSpaceResult") || 
                    paramTypes.contains("PNMembershipResult")) {
                    System.out.println("  Removing deprecated method: " + methodName);
                    return null;
                }
            }
            
            return method;
        }
        
        @Override
        public Node visit(ImportDeclaration n, Void arg) {
            ImportDeclaration importDecl = (ImportDeclaration) super.visit(n, arg);
            
            String importName = importDecl.getNameAsString();
            if (importName.contains("com.pubnub.api.models.consumer.pubsub.objects")) {
                if (importName.endsWith("PNUserResult")) {
                    importDecl.setName("com.pubnub.api.models.consumer.objects_api.uuid.PNUUIDMetadataResult");
                    changesCount++;
                    System.out.println("  Updated import: PNUserResult -> PNUUIDMetadataResult");
                } else if (importName.endsWith("PNSpaceResult")) {
                    importDecl.setName("com.pubnub.api.models.consumer.objects_api.channel.PNChannelMetadataResult");
                    changesCount++;
                    System.out.println("  Updated import: PNSpaceResult -> PNChannelMetadataResult");
                } else if (importName.endsWith("PNMembershipResult")) {
                    importDecl.setName("com.pubnub.api.models.consumer.objects_api.membership.PNMembershipResult");
                    changesCount++;
                    System.out.println("  Updated import: PNMembershipResult (objects package)");
                }
            }
            
            return importDecl;
        }
    }
}