package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    
    private static final Map<String, String> IMPORT_REPLACEMENTS = Map.ofEntries(
        // Command API changes - specific class replacements
        Map.entry("org.spongepowered.api.command.CommandSource", "org.spongepowered.api.command.CommandCause"),
        
        // Text API migration to Adventure
        Map.entry("org.spongepowered.api.text", "net.kyori.adventure.text"),
        Map.entry("org.spongepowered.api.text.serializer", "net.kyori.adventure.text.serializer"),
        
        // Event API changes
        Map.entry("org.spongepowered.api.event.game.state", "org.spongepowered.api.event.lifecycle"),
        
        // Data API changes
        Map.entry("org.spongepowered.api.data.key", "org.spongepowered.api.data"),
        
        // Math package changes
        Map.entry("com.flowpowered.math.vector", "org.spongepowered.math.vector")
    );
    
    private static final Map<String, String> PACKAGE_REPLACEMENTS = Map.of(
        "org.spongepowered.api.command.args", "org.spongepowered.api.command.parameter",
        "org.spongepowered.api.command.spec", "org.spongepowered.api.command.parameter"
    );
    
    private static final Map<String, String> STATIC_IMPORT_REPLACEMENTS = Map.of(
        "org.spongepowered.api.command.args.GenericArguments", "org.spongepowered.api.command.parameter.Parameter",
        "org.spongepowered.api.text.Text", "net.kyori.adventure.text.Component"
    );
    
private static final Map<String, String> TYPE_REPLACEMENTS = Map.of(
        "CommandSource", "CommandCause",
        "CommandSpec", "Command$Parameterized"
    );
    
    private static final Map<String, String> METHOD_REPLACEMENTS = Map.of(
        "sendMessage", "sendMessage",
        "hasPermission", "hasPermission",
        "getOne", "one",
        "requireOne", "requireOne"
    );
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        try {
            transformProject(sourceDir);
            System.out.println("Transformation completed successfully!");
        } catch (IOException e) {
            System.err.println("Error transforming project: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(Path sourceDir) throws IOException {
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".java")) {
                    transformJavaFile(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
    
    private static void transformJavaFile(Path javaFile) throws IOException {
        JavaParser parser = new JavaParser();
        Optional<CompilationUnit> cuOpt = parser.parse(javaFile).getResult();
        
        if (cuOpt.isEmpty()) {
            System.err.println("Warning: Could not parse " + javaFile);
            return;
        }
        
        CompilationUnit cu = cuOpt.get();
        
        // Apply transformations
        cu.accept(new ImportTransformer(), null);
        cu.accept(new TypeTransformer(), null);
        cu.accept(new MethodSignatureTransformer(), null);
        cu.accept(new MethodCallTransformer(), null);
        cu.accept(new StaticImportTransformer(), null);
        
        // Write the transformed file back
        Files.write(javaFile, cu.toString().getBytes());
    }
    
    private static class ImportTransformer extends ModifierVisitor<Void> {
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            
            // Check for exact import replacements
            for (Map.Entry<String, String> replacement : IMPORT_REPLACEMENTS.entrySet()) {
                if (importName.equals(replacement.getKey()) || 
                    importName.startsWith(replacement.getKey() + ".")) {
                    
                    String newImport = importName.replace(replacement.getKey(), replacement.getValue());
                    importDecl.setName(newImport);
                    break;
                }
            }
            
            // Handle star imports that need to be changed
            if (importDecl.isAsterisk()) {
                String basePackage = importName.substring(0, importName.length() - 2); // Remove ".*"
                for (Map.Entry<String, String> replacement : PACKAGE_REPLACEMENTS.entrySet()) {
                    if (basePackage.startsWith(replacement.getKey())) {
                        String newBase = basePackage.replace(replacement.getKey(), replacement.getValue());
                        importDecl.setName(newBase + ".*");
                        break;
                    }
                }
            }
            
            // Also check for package replacements (non-star imports that are part of old packages)
            for (Map.Entry<String, String> replacement : PACKAGE_REPLACEMENTS.entrySet()) {
                if (importName.startsWith(replacement.getKey() + ".") && !importDecl.isAsterisk()) {
                    String newImport = importName.replace(replacement.getKey(), replacement.getValue());
                    importDecl.setName(newImport);
                    break;
                }
            }
                
            return (Node) super.visit(importDecl, arg);
        }
    }
    
    private static class StaticImportTransformer extends ModifierVisitor<Void> {
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            if (importDecl.isStatic()) {
                String importName = importDecl.getNameAsString();
                for (Map.Entry<String, String> replacement : STATIC_IMPORT_REPLACEMENTS.entrySet()) {
                    if (importName.startsWith(replacement.getKey())) {
                        String newImport = importName.replace(replacement.getKey(), replacement.getValue());
                        importDecl.setName(newImport);
                        break;
                    }
                }
            }
            return (Node) super.visit(importDecl, arg);
        }
    }
    
    private static class TypeTransformer extends ModifierVisitor<Void> {
        @Override
        public Node visit(ClassOrInterfaceType type, Void arg) {
            String typeName = type.getNameAsString();
            
            // Check for type replacements
            for (Map.Entry<String, String> replacement : TYPE_REPLACEMENTS.entrySet()) {
                if (typeName.equals(replacement.getKey())) {
                    type.setName(replacement.getValue());
                    break;
                }
            }
            
            // Handle generic type parameters
            if (type.getTypeArguments().isPresent()) {
                NodeList<Type> typeArgs = type.getTypeArguments().get();
                for (Type typeArg : typeArgs) {
                    if (typeArg instanceof ClassOrInterfaceType) {
                        visit((ClassOrInterfaceType) typeArg, arg);
                    }
                }
            }
            
            return (Node) super.visit(type, arg);
        }
        
        @Override
        public Node visit(MethodDeclaration method, Void arg) {
            // Transform return type
            if (method.getType() != null) {
                method.getType().accept(this, arg);
            }
            
            // Transform parameter types
            for (Parameter param : method.getParameters()) {
                param.getType().accept(this, arg);
            }
            
            return (Node) super.visit(method, arg);
        }
        
        @Override
        public Node visit(FieldDeclaration field, Void arg) {
            field.getElementType().accept(this, arg);
            return (Node) super.visit(field, arg);
        }
        
        @Override
        public Node visit(VariableDeclarationExpr expr, Void arg) {
            expr.getElementType().accept(this, arg);
            return (Node) super.visit(expr, arg);
        }
    }
    
    private static class MethodSignatureTransformer extends ModifierVisitor<Void> {
        @Override
        public Node visit(MethodDeclaration method, Void arg) {
            String methodName = method.getNameAsString();
            
            // Transform execute method signature for CommandExecutor
            if ("execute".equals(methodName) && method.getParameters().size() == 2) {
                NodeList<Parameter> params = method.getParameters();
                if (params.size() >= 2) {
                    Parameter firstParam = params.get(0);
                    Parameter secondParam = params.get(1);
                    
                    // Check if this matches execute(CommandSource src, CommandContext args)
                    // or execute(CommandCause src, CommandContext args) after import transformation
                    String firstParamType = firstParam.getType().asString();
                    String secondParamType = secondParam.getType().asString();
                    if ((firstParamType.contains("CommandSource") || firstParamType.contains("CommandCause")) && 
                        secondParamType.contains("CommandContext")) {
                        
                        // Change to execute(CommandContext context)
                        Parameter newParam = new Parameter();
                        newParam.setType("org.spongepowered.api.command.parameter.CommandContext");
                        newParam.setName("context");
                        
                        method.setParameters(NodeList.nodeList(newParam));
                        
                        // Update method body to use context.cause() instead of src parameter
                        if (method.getBody().isPresent()) {
                            BlockStmt body = method.getBody().get();
                            transformMethodBody(body, firstParam.getNameAsString());
                        }
                    }
                }
            }
            
            return (Node) super.visit(method, arg);
        }
        
        private void transformMethodBody(BlockStmt body, String oldSrcParamName) {
            // Create a visitor to replace references to the old src parameter with context.cause()
            body.accept(new ModifierVisitor<Void>() {
                @Override
                public Node visit(NameExpr expr, Void arg) {
                    if (expr.getNameAsString().equals(oldSrcParamName)) {
                        // Replace src with context.cause()
                        MethodCallExpr causeCall = new MethodCallExpr(new NameExpr("context"), "cause");
                        return causeCall;
                    }
                    return (Node) super.visit(expr, arg);
                }
                
                @Override
                public Node visit(MethodCallExpr call, Void arg) {
                    // Update method calls that use the old src parameter
                    if (call.getScope().isPresent() && 
                        call.getScope().get() instanceof NameExpr) {
                        NameExpr scope = (NameExpr) call.getScope().get();
                        if (scope.getNameAsString().equals(oldSrcParamName)) {
                            // Change src.method() to context.cause().method()
                            MethodCallExpr causeCall = new MethodCallExpr(new NameExpr("context"), "cause");
                            call.setScope(causeCall);
                        }
                    }
return (Node) super.visit(call, arg);
                }
            }, null);
        }
    }
    
    private static class MethodCallTransformer extends ModifierVisitor<Void> {
        @Override
        public Node visit(MethodCallExpr call, Void arg) {
            String methodName = call.getNameAsString();
            
            // Update method names
            for (Map.Entry<String, String> replacement : METHOD_REPLACEMENTS.entrySet()) {
                if (methodName.equals(replacement.getKey())) {
                    // Check if we should rename this method call
                    if (shouldRenameMethod(call, replacement.getKey())) {
                        call.setName(replacement.getValue());
                    }
                }
            }
            
            // Special handling for Text.of() -> Component.text()
            if ("of".equals(methodName) && call.getScope().isPresent()) {
                Expression scope = call.getScope().get();
                if (scope instanceof NameExpr) {
                    NameExpr nameExpr = (NameExpr) scope;
                    if ("Text".equals(nameExpr.getNameAsString())) {
                        // Change Text.of() to Component.text()
                        call.setScope(new NameExpr("Component"));
                        call.setName("text");
                    }
                }
            }
            
            return (Node) super.visit(call, arg);
        }
        
        private boolean shouldRenameMethod(MethodCallExpr call, String methodName) {
            // Check if this is a call on a CommandContext object
            if (call.getScope().isPresent()) {
                Expression scope = call.getScope().get();
                if (scope instanceof NameExpr) {
                    String scopeName = ((NameExpr) scope).getNameAsString();
                    // If the scope is "args" (CommandContext parameter), rename getOne/requireOne
                    if ("args".equals(scopeName) && ("getOne".equals(methodName) || "requireOne".equals(methodName))) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}