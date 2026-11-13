package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.PrettyPrinter;
import com.github.javaparser.printer.configuration.PrettyPrinterConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = findJavaFiles(Paths.get(sourceDir));
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int modifiedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (processFile(javaFile)) {
                    modifiedFiles++;
                }
            }
            
            System.out.println("Modified " + modifiedFiles + " files");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(Path directory) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(directory)
             .filter(path -> path.toString().endsWith(".java"))
             .forEach(javaFiles::add);
        return javaFiles;
    }
    
    private static boolean processFile(Path javaFile) throws IOException {
        JavaParser parser = new JavaParser();
        Optional<CompilationUnit> cuOpt = parser.parse(javaFile).getResult();
        
        if (!cuOpt.isPresent()) {
            System.err.println("Failed to parse: " + javaFile);
            return false;
        }
        
        CompilationUnit cu = cuOpt.get();
        ScriptResultTransformer transformer = new ScriptResultTransformer();
        CompilationUnit modifiedCu = (CompilationUnit) cu.accept(transformer, null);
        
        if (transformer.isModified()) {
            PrettyPrinterConfiguration config = new PrettyPrinterConfiguration();
            config.setPrintComments(true);
            config.setEndOfLineCharacter("\n");
            PrettyPrinter printer = new PrettyPrinter(config);
            String newContent = printer.print(modifiedCu);
            
            Files.write(javaFile, newContent.getBytes());
            System.out.println("Modified: " + javaFile);
            return true;
        }
        
        return false;
    }
    
    static class ScriptResultTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean isModified() {
            return modified;
        }
        
        @Override
        public Node visit(ImportDeclaration id, Void arg) {
            String importName = id.getNameAsString();
            if (importName.equals("com.gargoylesoftware.htmlunit.ScriptResult")) {
                modified = true;
                return null; // Remove the import
            }
            return super.visit(id, arg);
        }
        
        @Override
        public Node visit(ObjectCreationExpr expr, Void arg) {
            // First visit children to handle nested expressions
            super.visit(expr, arg);
            
            com.github.javaparser.ast.type.Type type = expr.getType();
            if (type != null) {
                String typeName = type.toString();
                // Check for both simple name and fully qualified name
                if (typeName.equals("ScriptResult") || typeName.equals("com.gargoylesoftware.htmlunit.ScriptResult")) {
                    // Check if this is followed by .getJavaScriptResult()
                    Node parent = expr.getParentNode().orElse(null);
                    
                    if (parent instanceof MethodCallExpr) {
                        MethodCallExpr methodCall = (MethodCallExpr) parent;
                        if (methodCall.getNameAsString().equals("getJavaScriptResult") && 
                            methodCall.getScope().orElse(null) == expr) {
                            // Pattern: new ScriptResult(...).getJavaScriptResult()
                            // Replace the entire MethodCallExpr with the argument
                            NodeList<Expression> args = expr.getArguments();
                            if (args.size() == 1) {
                                modified = true;
                                return args.get(0);
                            }
                        }
                    } else {
                        // Pattern: new ScriptResult(...) without getJavaScriptResult()
                        // But this should probably be wrapped in something
                        // For now, we'll just replace with the argument
                        NodeList<Expression> args = expr.getArguments();
                        if (args.size() == 1) {
                            modified = true;
                            return args.get(0);
                        }
                    }
                }
            }
            
            return expr;
        }
        
        @Override
        public Node visit(VariableDeclarationExpr expr, Void arg) {
            super.visit(expr, arg);
            
            // Check if variable type is ScriptResult
            if (expr.getVariables().size() == 1) {
                com.github.javaparser.ast.type.Type variableType = expr.getVariable(0).getType();
                String typeName = variableType.toString();
                if (typeName.equals("ScriptResult") || typeName.equals("com.gargoylesoftware.htmlunit.ScriptResult")) {
                    // Change type to Object
                    modified = true;
                    expr.getVariable(0).setType(new ClassOrInterfaceType(null, "Object"));
                }
            }
            
            return expr;
        }
        
        @Override
        public Node visit(MethodCallExpr expr, Void arg) {
            super.visit(expr, arg);
            
            // Handle standalone getJavaScriptResult() calls
            // These would be on variables of type ScriptResult
            // Since we changed the type to Object, these calls need to be removed
            if (expr.getNameAsString().equals("getJavaScriptResult")) {
                // Check if the scope is a variable (not a new expression - that's handled above)
                Optional<Expression> scope = expr.getScope();
                if (scope.isPresent() && !(scope.get() instanceof ObjectCreationExpr)) {
                    // This is a method call on something that's not a new expression
                    // Just remove the method call, leaving the scope
                    modified = true;
                    return scope.get();
                }
            }
            
            return expr;
        }
    }
}