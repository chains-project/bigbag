package github;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import github.chains.JettyUpgradeChain;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class Transform {
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Transform <java-file>");
            System.exit(1);
        }
        
        try {
            String fileName = args[0];
            FileInputStream in = new FileInputStream(fileName);
            CompilationUnit cu = StaticJavaParser.parse(in);
            
            JettyUpgradeChain chain = new JettyUpgradeChain();
            Optional<CompilationUnit> result = chain.apply(cu);
            
            if (result.isPresent()) {
                System.out.println(result.get().toString());
            } else {
                System.err.println("Transformation failed");
                System.exit(1);
            }
        } catch (IOException e) {
            System.err.println("Error processing file: " + e.getMessage());
            System.exit(1);
        }
    }
}