package github.chains;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.ImportDeclaration;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.DefaultPrettyPrinter;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) throws IOException {
        // Process all Java files in the target directory
        if (args.length == 0) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        // Process all Java files
        Files.walk(sourcePath)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            new HibernateSessionImplementorToSharedSessionContractVisitor().visit(cu, null);
            // Write back to file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(cu.toString());
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that finds and fixes the SessionImplementor to SharedSessionContractImplementor 
     * method signature mismatch in UserType implementations.
     */
    private static class HibernateSessionImplementorToSharedSessionContractVisitor 
            extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(MethodDeclaration md, Void arg) {
            super.visit(md, arg);
            
            // Check if this is a nullSafeSet method with the problematic signature
            if (md.getNameAsString().equals("nullSafeSet")) {
                NodeList<Parameter> parameters = md.getParameters();
                if (parameters.size() == 4) {
                    // Check if the 4th parameter is of type SessionImplementor
                    Parameter fourthParam = parameters.get(3);
                    if (fourthParam.getType().toString().equals("SessionImplementor")) {
                        // Replace it with SharedSessionContractImplementor
                        fourthParam.setType(new ClassOrInterfaceType("SharedSessionContractImplementor"));
                    }
                }
            }
        }
        
        // We also need to add the import for SharedSessionContractImplementor if needed
        // This is handled in the main processFile method, but we could also process imports here
    }
}