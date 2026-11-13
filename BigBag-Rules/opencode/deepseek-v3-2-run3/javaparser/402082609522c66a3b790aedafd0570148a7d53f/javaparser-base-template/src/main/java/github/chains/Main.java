package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Applying SpongeAPI 8.0.0 migration transformations to: " + sourceDir);
        
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(javaFiles::add);
            
        int transformedFiles = 0;
        for (Path javaFile : javaFiles) {
            if (transformFile(javaFile)) {
                transformedFiles++;
            }
        }
        
        System.out.println("Transformed " + transformedFiles + " out of " + javaFiles.size() + " files");
    }
    
    private static boolean transformFile(Path file) throws IOException {
        String content = Files.readString(file);
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(content).getResult().orElseThrow();
        boolean modified = false;
        
        // 1. Transform imports
        modified |= transformImports(cu);
        
        // 2. Transform type references in class definitions
        modified |= transformClassTypeReferences(cu);
        
        // 3. Transform method signatures
        modified |= transformMethodSignatures(cu);
        
        // 4. Transform method calls
        modified |= transformMethodCalls(cu);
        
        // 5. Transform CommandSpec.builder() to Command.builder()
        modified |= transformCommandSpecBuilder(cu);
        
        if (modified) {
            Files.writeString(file, cu.toString());
            System.out.println("Transformed: " + file);
            return true;
        }
        
        return false;
    }
    
    private static boolean transformImports(CompilationUnit cu) {
        boolean modified = false;
        NodeList<ImportDeclaration> imports = cu.getImports();
        
        // Create a copy of imports to avoid ConcurrentModificationException
        List<ImportDeclaration> importsCopy = new ArrayList<>(imports);
        
        for (ImportDeclaration importDecl : importsCopy) {
            String importName = importDecl.getNameAsString();
            
            // Transform CommandSource import to CommandCause
            if (importName.equals("org.spongepowered.api.command.CommandSource")) {
                importDecl.setName("org.spongepowered.api.command.CommandCause");
                modified = true;
            }
            // Transform CommandException import
            else if (importName.equals("org.spongepowered.api.command.CommandException")) {
                importDecl.setName("org.spongepowered.api.command.exception.CommandException");
                modified = true;
            }
            // Transform CommandSpec import to Command
            else if (importName.equals("org.spongepowered.api.command.spec.CommandSpec")) {
                importDecl.setName("org.spongepowered.api.command.Command");
                modified = true;
            }
            // Remove old command.args imports (replaced by new parameter system)
            else if (importName.startsWith("org.spongepowered.api.command.args")) {
                cu.remove(importDecl);
                modified = true;
            }
            // Remove old command.spec imports
            else if (importName.startsWith("org.spongepowered.api.command.spec") && 
                     !importName.equals("org.spongepowered.api.command.spec.CommandSpec")) {
                cu.remove(importDecl);
                modified = true;
            }
        }
        
        // Add missing imports if needed
        boolean hasCommandCauseImport = imports.stream()
            .anyMatch(i -> i.getNameAsString().equals("org.spongepowered.api.command.CommandCause"));
        boolean hasCommandContextImport = imports.stream()
            .anyMatch(i -> i.getNameAsString().equals("org.spongepowered.api.command.parameter.CommandContext"));
        boolean hasCommandImport = imports.stream()
            .anyMatch(i -> i.getNameAsString().equals("org.spongepowered.api.command.Command"));
            
        // Check if file uses CommandSource/CommandCause
        String content = cu.toString();
        if (content.contains("CommandSource") && !hasCommandCauseImport) {
            cu.addImport("org.spongepowered.api.command.CommandCause");
            modified = true;
        }
        if (content.contains("CommandContext") && !hasCommandContextImport) {
            cu.addImport("org.spongepowered.api.command.parameter.CommandContext");
            modified = true;
        }
        if (content.contains("CommandSpec") && !hasCommandImport) {
            cu.addImport("org.spongepowered.api.command.Command");
            modified = true;
        }
        
        return modified;
    }
    
    private static boolean transformClassTypeReferences(CompilationUnit cu) {
        boolean modified = false;
        
        // Transform CommandSource to CommandCause in type references
        List<ClassOrInterfaceType> types = cu.findAll(ClassOrInterfaceType.class);
        for (ClassOrInterfaceType type : types) {
            if (type.getNameAsString().equals("CommandSource")) {
                type.setName("CommandCause");
                modified = true;
            }
            
            if (type.getNameAsString().equals("CommandSpec")) {
                type.setName("Command");
                modified = true;
            }
            
            // Transform PlatformPlugin<CommandSource> to PlatformPlugin<CommandCause>
            if (type.getNameAsString().equals("PlatformPlugin") && type.getTypeArguments().isPresent()) {
                NodeList<Type> typeArgs = type.getTypeArguments().get();
                for (int i = 0; i < typeArgs.size(); i++) {
                    if (typeArgs.get(i).toString().equals("CommandSource")) {
                        typeArgs.set(i, new ClassOrInterfaceType("CommandCause"));
                        modified = true;
                    }
                }
            }
        }
        
        return modified;
    }
    
    private static boolean transformMethodSignatures(CompilationUnit cu) {
        boolean modified = false;
        
        List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
        for (MethodDeclaration method : methods) {
            // Transform execute(CommandSource src, CommandContext args) to execute(CommandContext context)
            if (method.getNameAsString().equals("execute") && 
                method.getParameters().size() == 2 &&
                method.getParameter(0).getType().toString().equals("CommandSource") &&
                method.getParameter(1).getType().toString().equals("CommandContext")) {
                
                // Remove the first parameter (CommandSource src)
                method.getParameters().remove(0);
                
                // Rename the remaining parameter from 'args' to 'context' for clarity
                if (method.getParameter(0).getNameAsString().equals("args")) {
                    method.getParameter(0).setName("context");
                }
                
                // Update method body to adapt to new signature
                if (method.getBody().isPresent()) {
                    BlockStmt body = method.getBody().get();
                    
                    // Replace src with context.cause() in method calls
                    List<NameExpr> nameExprs = body.findAll(NameExpr.class);
                    for (NameExpr expr : nameExprs) {
                        if (expr.getNameAsString().equals("src")) {
                            MethodCallExpr causeCall = new MethodCallExpr(
                                new NameExpr("context"), "cause");
                            expr.replace(causeCall);
                        }
                        
                        if (expr.getNameAsString().equals("args")) {
                            expr.replace(new NameExpr("context"));
                        }
                    }
                }
                
                modified = true;
            }
        }
        
        return modified;
    }
    
    private static boolean transformMethodCalls(CompilationUnit cu) {
        boolean modified = false;
        
        // Transform plugin.sendMessage(src, key) to context.sendMessage(identity, message)
        List<MethodCallExpr> calls = cu.findAll(MethodCallExpr.class);
        for (MethodCallExpr call : calls) {
            if (call.getNameAsString().equals("sendMessage") && 
                call.getScope().isPresent() &&
                call.getScope().get().toString().endsWith("plugin") &&
                call.getArguments().size() == 2 &&
                call.getArgument(0).toString().equals("src")) {
                
                // Change to: context.sendMessage(context.cause().subject(), plugin.getMessage(key))
                // This is a generic transformation - actual implementation may vary
                call.setName("sendMessage");
                call.getArguments().set(0, new MethodCallExpr(new NameExpr("context"), "cause"));
                
                // Create a new argument for the message
                MethodCallExpr getMessageCall = new MethodCallExpr(
                    call.getScope().get(), "getMessage");
                getMessageCall.addArgument(call.getArgument(1));
                call.getArguments().set(1, getMessageCall);
                
                modified = true;
            }
        }
        
        return modified;
    }
    
    private static boolean transformCommandSpecBuilder(CompilationUnit cu) {
        boolean modified = false;
        
        // Transform CommandSpec.builder() to Command.builder()
        List<MethodCallExpr> calls = cu.findAll(MethodCallExpr.class);
        for (MethodCallExpr call : calls) {
            if (call.getNameAsString().equals("builder") && 
                call.getScope().isPresent() &&
                call.getScope().get().toString().equals("CommandSpec")) {
                
                call.setScope(new NameExpr("Command"));
                modified = true;
            }
        }
        
        // Transform return types from CommandSpec to Command
        List<ReturnStmt> returnStmts = cu.findAll(ReturnStmt.class);
        for (ReturnStmt stmt : returnStmts) {
            if (stmt.getExpression().isPresent()) {
                String expr = stmt.getExpression().get().toString();
                if (expr.contains("CommandSpec.builder()")) {
                    stmt.getExpression().get().replace(new MethodCallExpr(new NameExpr("Command"), "builder"));
                    modified = true;
                }
            }
        }
        
        return modified;
    }
}