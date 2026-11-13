package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    
    // Map old pipeline interfaces to new ones
    private static final Map<String, String> OLD_TO_NEW_INTERFACE = new HashMap<>();
    static {
        OLD_TO_NEW_INTERFACE.put("redis.clients.jedis.commands.BasicRedisPipeline", "redis.clients.jedis.commands.PipelineCommands");
        OLD_TO_NEW_INTERFACE.put("redis.clients.jedis.commands.BinaryRedisPipeline", "redis.clients.jedis.commands.PipelineBinaryCommands");
        OLD_TO_NEW_INTERFACE.put("redis.clients.jedis.commands.BinaryScriptingCommandsPipeline", "redis.clients.jedis.commands.RedisModulePipelineCommands");
        OLD_TO_NEW_INTERFACE.put("redis.clients.jedis.commands.MultiKeyBinaryRedisPipeline", "redis.clients.jedis.commands.PipelineBinaryCommands");
        OLD_TO_NEW_INTERFACE.put("redis.clients.jedis.commands.MultiKeyCommandsPipeline", "redis.clients.jedis.commands.PipelineCommands");
        OLD_TO_NEW_INTERFACE.put("redis.clients.jedis.commands.RedisPipeline", "redis.clients.jedis.commands.PipelineCommands");
        OLD_TO_NEW_INTERFACE.put("redis.clients.jedis.commands.ScriptingCommandsPipeline", "redis.clients.jedis.commands.RedisModulePipelineCommands");
    }
    
    // Simple names mapping
    private static final Map<String, String> SIMPLE_NAME_MAPPING = new HashMap<>();
    static {
        SIMPLE_NAME_MAPPING.put("BasicRedisPipeline", "PipelineCommands");
        SIMPLE_NAME_MAPPING.put("BinaryRedisPipeline", "PipelineBinaryCommands");
        SIMPLE_NAME_MAPPING.put("BinaryScriptingCommandsPipeline", "RedisModulePipelineCommands");
        SIMPLE_NAME_MAPPING.put("MultiKeyBinaryRedisPipeline", "PipelineBinaryCommands");
        SIMPLE_NAME_MAPPING.put("MultiKeyCommandsPipeline", "PipelineCommands");
        SIMPLE_NAME_MAPPING.put("RedisPipeline", "PipelineCommands");
        SIMPLE_NAME_MAPPING.put("ScriptingCommandsPipeline", "RedisModulePipelineCommands");
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Transforming Jedis pipeline/transaction code in: " + sourceDir);
        
        Files.walk(sourceDir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::transformFile);
        
        System.out.println("Transformation complete!");
    }
    
    private static void transformFile(Path filePath) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow();
            
            boolean modified = false;
            
            modified |= transformImports(cu);
            modified |= transformClassDeclarations(cu);
            modified |= transformGetResponseCalls(cu);
            
            if (modified) {
                System.out.println("Modified: " + filePath);
                Files.write(filePath, cu.toString().getBytes());
            }
        } catch (Exception e) {
            System.err.println("Error processing file: " + filePath + " - " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static boolean transformImports(CompilationUnit cu) {
        boolean modified = false;
        List<ImportDeclaration> importsToRemove = new ArrayList<>();
        Set<String> importsToAdd = new HashSet<>();
        
        // Collect existing imports
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            importsToAdd.add(importName);
        }
        
        // Transform pipeline interface imports
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            if (OLD_TO_NEW_INTERFACE.containsKey(importName)) {
                importsToRemove.add(importDecl);
                String newImport = OLD_TO_NEW_INTERFACE.get(importName);
                importsToAdd.remove(importName);
                importsToAdd.add(newImport);
                modified = true;
            }
        }
        
        // Remove ClusterPipeline import if present (it's a class, not an interface)
        for (ImportDeclaration importDecl : cu.getImports()) {
            if (importDecl.getNameAsString().equals("redis.clients.jedis.commands.ClusterPipeline")) {
                importsToRemove.add(importDecl);
                importsToAdd.remove("redis.clients.jedis.commands.ClusterPipeline");
                modified = true;
            }
        }
        
        if (modified) {
            cu.getImports().clear();
            for (String importName : importsToAdd) {
                cu.addImport(importName);
            }
        }
        
        return modified;
    }
    
    private static boolean transformClassDeclarations(CompilationUnit cu) {
        final boolean[] modified = {false};
        
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(ClassOrInterfaceDeclaration n, Void arg) {
                NodeList<ClassOrInterfaceType> implementedTypes = n.getImplementedTypes();
                
                // Check if any old pipeline interfaces are in the implements list
                boolean hasOldInterfaces = implementedTypes.stream()
                    .anyMatch(type -> SIMPLE_NAME_MAPPING.containsKey(type.getNameAsString()));
                
                if (hasOldInterfaces) {
                    List<ClassOrInterfaceType> newTypes = new ArrayList<>();
                    Set<String> addedTypes = new HashSet<>();
                    
                    // Add Closeable first if present
                    for (ClassOrInterfaceType type : implementedTypes) {
                        if (type.getNameAsString().equals("Closeable")) {
                            newTypes.add(type);
                            addedTypes.add("Closeable");
                            break;
                        }
                    }
                    
                    // Add transformed pipeline interfaces
                    for (ClassOrInterfaceType type : implementedTypes) {
                        String typeName = type.getNameAsString();
                        String newTypeName = SIMPLE_NAME_MAPPING.get(typeName);
                        
                        if (newTypeName != null && !addedTypes.contains(newTypeName)) {
                            newTypes.add(new ClassOrInterfaceType(null, newTypeName));
                            addedTypes.add(newTypeName);
                            modified[0] = true;
                        } else if (!SIMPLE_NAME_MAPPING.containsKey(typeName) && 
                                   !typeName.equals("ClusterPipeline") && 
                                   !addedTypes.contains(typeName)) {
                            // Keep non-pipeline types
                            newTypes.add(type);
                            addedTypes.add(typeName);
                        }
                    }
                    
                    if (modified[0]) {
                        n.getImplementedTypes().clear();
                        n.getImplementedTypes().addAll(newTypes);
                    }
                }
                
                return super.visit(n, arg);
            }
        }, null);
        
        return modified[0];
    }
    
    private static boolean transformGetResponseCalls(CompilationUnit cu) {
        final boolean[] modified = {false};
        
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(MethodCallExpr n, Void arg) {
                if (n.getNameAsString().equals("getResponse")) {
                    NodeList<Expression> arguments = n.getArguments();
                    if (arguments.size() == 1) {
                        Expression argExpr = arguments.get(0);
                        
                        if (argExpr instanceof ObjectCreationExpr) {
                            ObjectCreationExpr objectCreation = (ObjectCreationExpr) argExpr;
                            ClassOrInterfaceType type = objectCreation.getType();
                            
                            if (type.getNameAsString().equals("Builder")) {
                                // Transform: getResponse(new Builder<ResultSet>() { ... })
                                // to: appendCommand(new CommandObject<>(commandArguments(RedisGraphCommand.QUERY), new Builder<ResultSet>() { ... }))
                                
                                MethodCallExpr appendCommandCall = new MethodCallExpr();
                                appendCommandCall.setScope(n.getScope().orElse(null));
                                appendCommandCall.setName("appendCommand");
                                
                                ObjectCreationExpr commandObjectCreation = new ObjectCreationExpr();
                                commandObjectCreation.setType(new ClassOrInterfaceType(null, "CommandObject"));
                                
                                NodeList<Expression> commandObjectArgs = new NodeList<>();
                                
                                // Create commandArguments call
                                MethodCallExpr commandArgsCreation = new MethodCallExpr();
                                commandArgsCreation.setName("commandArguments");
                                commandArgsCreation.addArgument(new NameExpr("RedisGraphCommand.QUERY"));
                                commandObjectArgs.add(commandArgsCreation);
                                
                                // Add the original builder
                                commandObjectArgs.add(argExpr);
                                
                                commandObjectCreation.setArguments(commandObjectArgs);
                                appendCommandCall.addArgument(commandObjectCreation);
                                
                                modified[0] = true;
                                return super.visit(appendCommandCall, arg);
                            }
                        } else if (argExpr instanceof FieldAccessExpr) {
                            FieldAccessExpr fieldAccess = (FieldAccessExpr) argExpr;
                            if (fieldAccess.getNameAsString().equals("STRING")) {
                                // Transform: getResponse(BuilderFactory.STRING)
                                // to: appendCommand(new CommandObject<>(commandArguments(RedisGraphCommand.DELETE), BuilderFactory.STRING))
                                
                                MethodCallExpr appendCommandCall = new MethodCallExpr();
                                appendCommandCall.setScope(n.getScope().orElse(null));
                                appendCommandCall.setName("appendCommand");
                                
                                ObjectCreationExpr commandObjectCreation = new ObjectCreationExpr();
                                commandObjectCreation.setType(new ClassOrInterfaceType(null, "CommandObject"));
                                
                                NodeList<Expression> commandObjectArgs = new NodeList<>();
                                
                                // Create commandArguments call
                                MethodCallExpr commandArgsCreation = new MethodCallExpr();
                                commandArgsCreation.setName("commandArguments");
                                commandArgsCreation.addArgument(new NameExpr("RedisGraphCommand.DELETE"));
                                commandObjectArgs.add(commandArgsCreation);
                                
                                // Add the builder factory
                                commandObjectArgs.add(argExpr);
                                
                                commandObjectCreation.setArguments(commandObjectArgs);
                                appendCommandCall.addArgument(commandObjectCreation);
                                
                                modified[0] = true;
                                return super.visit(appendCommandCall, arg);
                            }
                        }
                    }
                }
                return super.visit(n, arg);
            }
        }, null);
        
        return modified[0];
    }
}