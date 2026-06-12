package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        try {
            processDirectory(new File(sourceDirectory));
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processDirectory(File directory) throws IOException {
        if (directory.isFile() && directory.getName().endsWith(".java")) {
            processFile(directory);
        } else if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    processDirectory(file);
                }
            }
        }
    }
    
    private static void processFile(File file) throws IOException {
        String content = new String(Files.readAllBytes(file.toPath()));
        CompilationUnit cu = StaticJavaParser.parse(content);
        
        // Find all CalculatorFacade setCsvReader method calls and replace them
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr methodCall, Void arg) {
                if (isSetCsvReaderCall(methodCall)) {
                    // Check if it has no arguments (old API) and replace with boolean true
                    if (methodCall.getArguments().isEmpty()) {
                        methodCall.setArguments(NodeList.nodeList(new BooleanLiteralExpr(true)));
                    }
                }
                super.visit(methodCall, arg);
            }
        }, null);
        
        // Write back the modified content
        try {
            Files.write(file.toPath(), cu.toString().getBytes("UTF-8"));
        } catch (IOException e) {
            System.err.println("Failed to write file " + file.getAbsolutePath() + ": " + e.getMessage());
        }
    }
    
    private static boolean isSetCsvReaderCall(MethodCallExpr methodCall) {
        // Check if method name is setCsvReader
        if (!"setCsvReader".equals(methodCall.getNameAsString())) {
            return false;
        }
        
        // Check if the method is called on a CalculatorFacade instance
        if (methodCall.getScope().isPresent()) {
            var scope = methodCall.getScope().get();
            if (scope instanceof NameExpr) {
                NameExpr nameExpr = (NameExpr) scope;
                // Check if it's a CalculatorFacade reference or a subclass
                return nameExpr.getNameAsString().contains("CalculatorFacade") || 
                       nameExpr.getNameAsString().contains("MarcJsonCalculatorFacade");
            }
        }
        return false;
    }
}