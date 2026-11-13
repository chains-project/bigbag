package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;

public class ThriftTransportTransformer {

    public static void main(String[] args) throws IOException {
        // Process all Java files containing TFramedTransport
        String[] files = {
            "/workspace/singer/singer/src/main/java/com/pinterest/singer/reader/ThriftReader.java",
            "/workspace/singer/singer/src/main/java/com/pinterest/singer/utils/SimpleThriftLogger.java"
        };

        for (String filePath : files) {
            transformFile(filePath);
        }
        
        System.out.println("Transformation completed for all files.");
    }

    public static void transformFile(String filePath) throws IOException {
        System.out.println("Processing: " + filePath);
        
        CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(filePath));
        
        boolean modified = false;
        
        // Remove TFramedTransport import
        NodeList<ImportDeclaration> imports = cu.getImports();
        for (int i = imports.size() - 1; i >= 0; i--) {
            ImportDeclaration imp = imports.get(i);
            if (imp.getNameAsString().contains("TFramedTransport")) {
                imports.remove(i);
                modified = true;
            }
        }
        
        // Special handling for ThriftReader.java
        if (filePath.contains("ThriftReader.java")) {
            // Find class declaration
            ClassOrInterfaceDeclaration clazz = cu.getClassByName("ThriftReader").orElse(null);
            if (clazz != null) {
                // Handle field declaration: private final TFramedTransport framedTransport;
                for (FieldDeclaration field : clazz.getFields()) {
                    if (field.getElementType().toString().equals("TFramedTransport")) {
                        // Change to TTransport instead of TFramedTransport
                        field.getElementType().setName("TTransport");
                        modified = true;
                    }
                }
                
                // Handle constructor that initializes TFramedTransport
                for (ConstructorDeclaration constructor : clazz.getConstructors()) {
                    NodeList<Statement> statements = constructor.getBody().getStatements();
                    for (int i = 0; i < statements.size(); i++) {
                        Statement stmt = statements.get(i);
                        if (stmt instanceof ExpressionStmt) {
                            ExpressionStmt exprStmt = (ExpressionStmt) stmt;
                            Expression expr = exprStmt.getExpression();
                            
                            if (expr instanceof AssignmentExpr) {
                                AssignmentExpr assignExpr = (AssignmentExpr) expr;
                                Expression rhs = assignExpr.getValue();
                                
                                if (rhs instanceof ObjectCreationExpr) {
                                    ObjectCreationExpr creationExpr = (ObjectCreationExpr) rhs;
                                    
                                    if (creationExpr.getType().getNameAsString().equals("TFramedTransport")) {
                                        // Replace with TIOStreamTransport
                                        creationExpr.setType(new ClassOrInterfaceType("TIOStreamTransport"));
                                        // Update the arguments to match TIOStreamTransport constructor
                                        NodeList<Expression> args = creationExpr.getArguments();
                                        if (args.size() > 0) {
                                            // We need to wrap the existing argument in a TIOStreamTransport constructor
                                            args.clear();
                                            args.add(new ObjectCreationExpr()
                                                .setType(new ClassOrInterfaceType("TIOStreamTransport"))
                                                .setArguments(new NodeList<>(new NameExpr("byteOffsetInputStream")))
                                            );
                                        }
                                        modified = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Special handling for SimpleThriftLogger.java
        if (filePath.contains("SimpleThriftLogger.java")) {
            // Find class declaration
            ClassOrInterfaceDeclaration clazz = cu.getClassByName("SimpleThriftLogger").orElse(null);
            if (clazz != null) {
                // Find the ByteOffsetTFramedTransport inner class and replace it
                for (ClassOrInterfaceDeclaration innerClass : clazz.getMembersOfClass()) {
                    if (innerClass.getNameAsString().equals("ByteOffsetTFramedTransport")) {
                        // Change the class to extend TTransport instead of TFramedTransport
                        innerClass.setExtendedTypes(new NodeList<>());
                        innerClass.addExtendedType("TTransport");
                        modified = true;
                    }
                }
                
                // Handle constructor that initializes TFramedTransport
                for (ConstructorDeclaration constructor : clazz.getConstructors()) {
                    NodeList<Statement> statements = constructor.getBody().getStatements();
                    for (int i = 0; i < statements.size(); i++) {
                        Statement stmt = statements.get(i);
                        if (stmt instanceof ExpressionStmt) {
                            ExpressionStmt exprStmt = (ExpressionStmt) stmt;
                            Expression expr = exprStmt.getExpression();
                            
                            if (expr instanceof AssignmentExpr) {
                                AssignmentExpr assignExpr = (AssignmentExpr) expr;
                                Expression rhs = assignExpr.getValue();
                                
                                if (rhs instanceof ObjectCreationExpr) {
                                    ObjectCreationExpr creationExpr = (ObjectCreationExpr) rhs;
                                    
                                    if (creationExpr.getType().getNameAsString().equals("ByteOffsetTFramedTransport")) {
                                        // Replace with TIOStreamTransport wrapped in a new constructor
                                        // But we need to make sure we're properly replacing the whole expression
                                        creationExpr.setType(new ClassOrInterfaceType("TIOStreamTransport"));
                                        modified = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // If we made any changes, save the file
        if (modified) {
            try (FileWriter writer = new FileWriter(filePath)) {
                cu.accept(new com.github.javaparser.printer.JavaPrinter(), writer);
            }
            System.out.println("Modified: " + filePath);
        } else {
            System.out.println("No changes needed: " + filePath);
        }
    }
}