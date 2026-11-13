package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int modifiedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (processFile(javaFile.toFile())) {
                    modifiedFiles++;
                }
            }
            
            System.out.println("Successfully modified " + modifiedFiles + " files");
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static boolean processFile(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            JavaParser parser = new JavaParser();
            ParseResult<CompilationUnit> parseResult = parser.parse(in);
            
            if (!parseResult.isSuccessful()) {
                System.err.println("Failed to parse " + file.getPath());
                return false;
            }
            
            CompilationUnit cu = parseResult.getResult().get();
            
            PrintToTransformer transformer = new PrintToTransformer();
            CompilationUnit transformedCu = (CompilationUnit) cu.accept(transformer, null);
            
            if (transformer.wasModified()) {
                try (FileOutputStream out = new FileOutputStream(file)) {
                    out.write(transformedCu.toString().getBytes());
                }
                System.out.println("Modified: " + file.getPath());
                return true;
            }
            
        } catch (IOException e) {
            System.err.println("Error processing file " + file.getPath() + ": " + e.getMessage());
        }
        
        return false;
    }
    
    private static class PrintToTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Look for patterns like: DateTimeFormatter.printTo(StringBuilder, ReadablePartial)
            // or DateTimeFormatter.printTo(StringBuilder, ReadableInstant)
            
            if (n.getNameAsString().equals("printTo")) {
                // Check if this is a DateTimeFormatter.printTo call
                if (n.getScope().isPresent()) {
                    String scopeName = n.getScope().get().toString();
                    
                    // Check if the first argument is a StringBuilder/StringBuffer
                    if (n.getArguments().size() >= 2) {
                        String firstArg = n.getArgument(0).toString();
                        
                        // Check if first argument is StringBuilder/StringBuffer variable
                        if (firstArg.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*$")) {
                            // Transform: formatter.printTo(builder, partial) -> builder.append(formatter.print(partial))
                            MethodCallExpr printCall = new MethodCallExpr(
                                n.getScope().get(),
                                new SimpleName("print"),
                                NodeList.nodeList(n.getArgument(1))
                            );
                            
                            MethodCallExpr appendCall = new MethodCallExpr(
                                new NameExpr(firstArg),
                                new SimpleName("append"),
                                NodeList.nodeList(printCall)
                            );
                            
                            modified = true;
                            return appendCall;
                        }
                    }
                }
            }
            
            return super.visit(n, arg);
        }
    }
}