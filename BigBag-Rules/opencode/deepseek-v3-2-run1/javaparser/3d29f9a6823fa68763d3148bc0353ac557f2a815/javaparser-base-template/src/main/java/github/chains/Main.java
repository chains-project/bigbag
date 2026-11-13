package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        System.out.println("Processing source directory: " + sourceDir);
        
        List<Path> javaFiles = Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
            
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        JavaParser javaParser = new JavaParser();
        int modifiedFiles = 0;
        
        for (Path javaFile : javaFiles) {
            CompilationUnit cu = javaParser.parse(javaFile).getResult().orElse(null);
            if (cu == null) {
                continue;
            }
            
            AddEnabledLanguagesFixVisitor visitor = new AddEnabledLanguagesFixVisitor();
            cu.accept(visitor, null);
            boolean modified = visitor.modified;
            if (modified) {
                // Write back the modified file
                PrinterConfiguration config = new DefaultPrinterConfiguration();
                DefaultPrettyPrinter printer = new DefaultPrettyPrinter(config);
                String newContent = printer.print(cu);
                Files.write(javaFile, newContent.getBytes());
                modifiedFiles++;
                System.out.println("Modified: " + javaFile);
            }
        }
        
        System.out.println("Total files modified: " + modifiedFiles);
    }
    
    static class AddEnabledLanguagesFixVisitor extends ModifierVisitor<Void> {
        boolean modified = false;
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check if this is a call to addEnabledLanguages
            if (n.getNameAsString().equals("addEnabledLanguages")) {
                // Check if it has exactly one argument
                if (n.getArguments().size() == 1) {
                    Expression argExpr = n.getArgument(0);
                    String argStr = argExpr.toString();
                    
                    // Check if this is a call on AnalysisEngineConfiguration.Builder
                    // by looking at the scope
                    String scope = n.getScope().map(Expression::toString).orElse("");
                    if (scope.contains("AnalysisEngineConfiguration") || scope.contains("Builder")) {
                        // This might need more complex transformation
                        // For now, just transform the argument
                        String newArgStr = argStr + ".toArray(new Language[0])";
                        n.setArgument(0, new NameExpr(newArgStr));
                        modified = true;
                        
                        System.out.println("Transformed addEnabledLanguages call on " + scope + ": " + argStr + " -> " + newArgStr);
                        System.out.println("Warning: AnalysisEngineConfiguration.Builder might not have addEnabledLanguages method in new API");
                        return n;
                    } else {
                        // Transform: addEnabledLanguages(set) -> addEnabledLanguages(set.toArray(new Language[0]))
                        String newArgStr = argStr + ".toArray(new Language[0])";
                        n.setArgument(0, new NameExpr(newArgStr));
                        modified = true;
                        
                        System.out.println("Transformed addEnabledLanguages call: " + argStr + " -> " + newArgStr);
                        return n;
                    }
                }
            }
            return super.visit(n, arg);
        }
    }
}