package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic transformation rule for libthrift 0.16.0 breaking change:
 * TSerializer() constructor now throws TTransportException
 * 
 * This transformation moves field initialization with new TSerializer() 
 * from field declarations to constructors, wrapping in try-catch blocks.
 */
public class Main {
    
    // Configuration: Fully-qualified type names from the dependency
    private static final String TSERIALIZER_TYPE = "org.apache.thrift.TSerializer";
    private static final String TTRANSPORT_EXCEPTION_TYPE = "org.apache.thrift.transport.TTransportException";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = findJavaFiles(Paths.get(sourceDir));
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int transformedFiles = 0;
            int transformedCalls = 0;
            
            for (Path javaFile : javaFiles) {
                boolean fileModified = processFile(javaFile);
                if (fileModified) {
                    transformedFiles++;
                    transformedCalls += countTSerializerCalls(javaFile);
                }
            }
            
            System.out.println("Transformation completed:");
            System.out.println("  - Files modified: " + transformedFiles);
            System.out.println("  - TSerializer calls wrapped: " + transformedCalls);
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(Path rootDir) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(rootDir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        return javaFiles;
    }
    
    private static boolean processFile(Path javaFile) throws IOException {
        String content = Files.readString(javaFile);
        JavaParser parser = new JavaParser();
        ParseResult<CompilationUnit> parseResult = parser.parse(content);
        
        if (!parseResult.isSuccessful()) {
            System.err.println("Failed to parse: " + javaFile);
            return false;
        }
        
        CompilationUnit cu = parseResult.getResult().get();
        boolean modified = false;
        
        // Find all ObjectCreationExpr nodes that create TSerializer instances
        List<ObjectCreationExpr> tserializerCreations = cu.findAll(ObjectCreationExpr.class, 
            expr -> isTSerializerCreation(expr));
        
        for (ObjectCreationExpr creation : tserializerCreations) {
            // Check if this is a field declaration
            Node parent = creation.getParentNode().orElse(null);
            if (parent instanceof VariableDeclarator) {
                VariableDeclarator varDecl = (VariableDeclarator) parent;
                Node grandParent = varDecl.getParentNode().orElse(null);
                
                // If this is a field declaration, move initialization to constructor
                if (grandParent != null && isFieldDeclarationContext(grandParent)) {
                    moveFieldInitializationToConstructor(varDecl, creation, cu);
                    modified = true;
                }
            }
        }
        
        if (modified) {
            // Write the modified content back to the file
            String transformedContent = cu.toString();
            Files.writeString(javaFile, transformedContent);
            System.out.println("Modified: " + javaFile);
        }
        
        return modified;
    }
    
    private static boolean isTSerializerCreation(ObjectCreationExpr expr) {
        return expr.getType().asString().equals("TSerializer") ||
               (expr.getType() instanceof ClassOrInterfaceType &&
                ((ClassOrInterfaceType) expr.getType()).getNameAsString().equals("TSerializer"));
    }
    
    private static boolean isFieldDeclarationContext(Node node) {
        // Check if this node represents a field declaration context
        // (not inside a method body)
        return !(node.findAncestor(MethodDeclaration.class).isPresent());
    }
    
    private static void moveFieldInitializationToConstructor(VariableDeclarator varDecl, 
                                                           ObjectCreationExpr creation, 
                                                           CompilationUnit cu) {
        // Get the field name
        String fieldName = varDecl.getNameAsString();
        
        // Remove the initialization from the field declaration
        varDecl.removeInitializer();
        
        // Find or create a constructor in the containing class
        ClassOrInterfaceDeclaration containingClass = varDecl.findAncestor(ClassOrInterfaceDeclaration.class).get();
        
        // Look for existing constructors
        List<ConstructorDeclaration> constructors = containingClass.findAll(ConstructorDeclaration.class);
        
            if (constructors.isEmpty()) {
            // Create a default constructor if none exists
            ConstructorDeclaration defaultConstructor = new ConstructorDeclaration();
            defaultConstructor.setName(containingClass.getNameAsString());
            defaultConstructor.setModifiers(new NodeList<>());
            defaultConstructor.setBody(new BlockStmt());
            containingClass.addMember(defaultConstructor);
            constructors = containingClass.findAll(ConstructorDeclaration.class);
        }
        
        // Add the initialization to the first constructor
        ConstructorDeclaration constructor = constructors.get(0);
        BlockStmt constructorBody = constructor.getBody();
        
        // Create try-catch block for TSerializer initialization
        BlockStmt tryBlock = new BlockStmt();
        tryBlock.addStatement(
            new com.github.javaparser.ast.stmt.ExpressionStmt(
                new com.github.javaparser.ast.expr.AssignExpr(
                    new com.github.javaparser.ast.expr.FieldAccessExpr(
                        new com.github.javaparser.ast.expr.ThisExpr(),
                        fieldName
                    ),
                    creation,
                    com.github.javaparser.ast.expr.AssignExpr.Operator.ASSIGN
                )
            )
        );
        
        // Create catch clause for TTransportException
        CatchClause catchClause = new CatchClause();
        catchClause.setParameter(
            new com.github.javaparser.ast.body.Parameter(
                new ClassOrInterfaceType(null, "TTransportException"),
                "e"
            )
        );
        
        BlockStmt catchBlock = new BlockStmt();
        catchBlock.addStatement(
            new com.github.javaparser.ast.stmt.ThrowStmt(
                new com.github.javaparser.ast.expr.ObjectCreationExpr(
                    null,
                    new ClassOrInterfaceType(null, "RuntimeException"),
                    new NodeList<>(
                        new com.github.javaparser.ast.expr.StringLiteralExpr("Failed to initialize TSerializer"),
                        new com.github.javaparser.ast.expr.NameExpr("e")
                    )
                )
            )
        );
        catchClause.setBody(catchBlock);
        
        TryStmt tryStmt = new TryStmt();
        tryStmt.setTryBlock(tryBlock);
        tryStmt.setCatchClauses(new NodeList<>(catchClause));
        
        // Add the try-catch block to the constructor body
        constructorBody.addStatement(tryStmt);
    }
    
    private static int countTSerializerCalls(Path javaFile) throws IOException {
        String content = Files.readString(javaFile);
        JavaParser parser = new JavaParser();
        ParseResult<CompilationUnit> parseResult = parser.parse(content);
        
        if (!parseResult.isSuccessful()) {
            return 0;
        }
        
        CompilationUnit cu = parseResult.getResult().get();
        List<ObjectCreationExpr> tserializerCreations = cu.findAll(ObjectCreationExpr.class, 
            expr -> isTSerializerCreation(expr));
        
        return tserializerCreations.size();
    }
}