package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

public class Main {
    private static final Map<String, String> IMPORT_MAPPINGS = new HashMap<>();
    private static final Map<String, String> METHOD_MAPPINGS = new HashMap<>();
    
    static {
        IMPORT_MAPPINGS.put(
            "com.pubnub.api.models.consumer.pubsub.objects.PNUserResult",
            "com.pubnub.api.models.consumer.objects_api.uuid.PNUUIDMetadataResult"
        );
        IMPORT_MAPPINGS.put(
            "com.pubnub.api.models.consumer.pubsub.objects.PNSpaceResult",
            "com.pubnub.api.models.consumer.objects_api.channel.PNChannelMetadataResult"
        );
        IMPORT_MAPPINGS.put(
            "com.pubnub.api.models.consumer.pubsub.objects.PNMembershipResult",
            "com.pubnub.api.models.consumer.objects_api.membership.PNMembershipResult"
        );
        
        METHOD_MAPPINGS.put("user", "uuid");
        METHOD_MAPPINGS.put("space", "channel");
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        processDirectory(new File(sourceDir));
        System.out.println("Transformation completed successfully");
    }
    
    private static void processDirectory(File directory) throws Exception {
        File[] files = directory.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                processDirectory(file);
            } else if (file.getName().endsWith(".java")) {
                processJavaFile(file);
            }
        }
    }
    
    private static void processJavaFile(File javaFile) throws Exception {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow();
        
        PubNubTransformer transformer = new PubNubTransformer();
        transformer.visit(cu, null);
        
        System.out.println("Transforming: " + javaFile.getPath());
        
        try (FileWriter writer = new FileWriter(javaFile)) {
            writer.write(cu.toString());
        }
    }
    
    static class PubNubTransformer extends ModifierVisitor<Void> {
        @Override
        public ObjectCreationExpr visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            
            if (n.getType().toString().equals("PNConfiguration") && n.getArguments().isEmpty()) {
                n.setArguments(new NodeList<>(new StringLiteralExpr("")));
                System.out.println("  Fixed PNConfiguration constructor: added empty string argument");
            }
            
            return n;
        }
        
        @Override
        public ImportDeclaration visit(ImportDeclaration n, Void arg) {
            super.visit(n, arg);
            
            String importStr = n.getNameAsString();
            if (IMPORT_MAPPINGS.containsKey(importStr)) {
                String newImport = IMPORT_MAPPINGS.get(importStr);
                System.out.println("  Updated import: " + importStr + " -> " + newImport);
                n.setName(newImport);
            }
            
            return n;
        }
        
        @Override
        public MethodDeclaration visit(MethodDeclaration n, Void arg) {
            super.visit(n, arg);
            
            String methodName = n.getNameAsString();
            
            if (METHOD_MAPPINGS.containsKey(methodName) && n.getParameters().size() == 2) {
                String newMethodName = METHOD_MAPPINGS.get(methodName);
                n.setName(newMethodName);
                
                if (methodName.equals("user")) {
                    n.getParameter(1).setType(new ClassOrInterfaceType(null, "PNUUIDMetadataResult"));
                    System.out.println("  Updated method: user() -> uuid() with PNUUIDMetadataResult parameter");
                } else if (methodName.equals("space")) {
                    n.getParameter(1).setType(new ClassOrInterfaceType(null, "PNChannelMetadataResult"));
                    System.out.println("  Updated method: space() -> channel() with PNChannelMetadataResult parameter");
                }
            }
            
            if (methodName.equals("membership") && n.getParameters().size() == 2) {
                String paramType = n.getParameter(1).getType().toString();
                if (paramType.contains("PNMembershipResult") && !paramType.contains("objects_api.membership")) {
                    n.getParameter(1).setType(new ClassOrInterfaceType(null, "PNMembershipResult"));
                    System.out.println("  Updated membership() method parameter type to new package");
                }
            }
            
            return n;
        }
        
        @Override
        public ClassOrInterfaceDeclaration visit(ClassOrInterfaceDeclaration n, Void arg) {
            super.visit(n, arg);
            
            if (n.getExtendedTypes().stream().anyMatch(t -> 
                t.getNameAsString().equals("SubscribeCallback") ||
                t.getNameAsString().equals("SubscribeCallback.BaseSubscribeCallback"))) {
                
                boolean hasFileMethod = n.getMethods().stream()
                    .anyMatch(m -> m.getNameAsString().equals("file"));
                
                if (!hasFileMethod) {
                    MethodDeclaration fileMethod = new MethodDeclaration();
                    fileMethod.setName("file");
                    fileMethod.addModifier(com.github.javaparser.ast.Modifier.Keyword.PUBLIC);
                    fileMethod.addAnnotation("Override");
                    
                    fileMethod.addParameter(
                        new Parameter(
                            new ClassOrInterfaceType(null, "PubNub"),
                            "pubnub"
                        )
                    );
                    fileMethod.addParameter(
                        new Parameter(
                            new ClassOrInterfaceType(null, "PNFileEventResult"),
                            "pnFileEventResult"
                        )
                    );
                    
                    BlockStmt body = new BlockStmt();
                    fileMethod.setBody(body);
                    
                    n.addMember(fileMethod);
                    System.out.println("  Added missing file() method to SubscribeCallback implementation");
                }
            }
            
            return n;
        }
    }
}