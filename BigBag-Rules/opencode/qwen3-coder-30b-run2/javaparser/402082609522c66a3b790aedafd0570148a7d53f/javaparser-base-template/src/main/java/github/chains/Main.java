package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.utils.SourceRoot;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        // Process all Java files in the specified directory
        String sourceDir = args.length > 0 ? args[0] : "/workspace/ChangeSkin/sponge/src/main/java";
        
        try {
            SourceRoot sourceRoot = new SourceRoot(Paths.get(sourceDir));
            
            // Parse all Java files
            sourceRoot.parse("", (localPath, absolutePath, compilationUnit) -> {
                if (compilationUnit.isPresent()) {
                    CompilationUnit cu = compilationUnit.get();
                    
                    // Apply transformations
                    TransformVisitor visitor = new TransformVisitor();
                    cu.accept(visitor, null);
                }
                return null;
            });
            
            // Save modified files back to disk
            sourceRoot.saveAll();
            
        } catch (Exception e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    static class TransformVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(ImportDeclaration importDecl, Void arg) {
            String importedName = importDecl.getNameAsString();
            
            // Handle command-related package changes
            if (importedName.equals("org.spongepowered.api.command.CommandSource")) {
                // Replace with adventure audience
                importDecl.setName("net.kyori.adventure.audience.Audience");
            } else if (importedName.equals("org.spongepowered.api.command.CommandManager")) {
                // Remove it - commands now handled through different API
                return null;
            } else if (importedName.equals("org.spongepowered.api.command.CommandException")) {
                // Remove it - no longer exists in this form
                return null;
            } else if (importedName.contains(".args") || importedName.contains(".spec")) {
                // Remove command args and spec imports
                return null;
            }
            
            // Handle event lifecycle changes
            if (importedName.startsWith("org.spongepowered.api.event.game.state")) {
                // Replace with lifecycle events
                importDecl.setName(importedName.replace("org.spongepowered.api.event.game.state", "org.spongepowered.api.event.lifecycle"));
            }
            
            // Handle network changes
            if (importedName.startsWith("org.spongepowered.api.network.ChannelBinding")) {
                // Replace with new network API
                importDecl.setName("org.spongepowered.api.network.channel.ChannelBinding");
            }
            
            if (importedName.startsWith("org.spongepowered.api.network.ChannelRegistrar")) {
                // Replace with new network API
                importDecl.setName("org.spongepowered.api.network.channel.ChannelRegistrar");
            }
            
            if (importedName.startsWith("org.spongepowered.api.network.ChannelBuf")) {
                // Replace with new network API
                importDecl.setName("org.spongepowered.api.network.channel.ChannelBuf");
            }
            
            if (importedName.startsWith("org.spongepowered.api.network.RawDataListener")) {
                // Replace with new network API
                importDecl.setName("org.spongepowered.api.network.channel.RawDataListener");
            }
            
            if (importedName.startsWith("org.spongepowered.api.network.RawDataChannel")) {
                // Replace with new network API
                importDecl.setName("org.spongepowered.api.network.channel.RawDataChannel");
            }
            
            // Handle text serializer changes
            if (importedName.startsWith("org.spongepowered.api.text.serializer")) {
                importDecl.setName("net.kyori.adventure.text.serializer");
            }
            
            // Handle text package changes
            if (importedName.startsWith("org.spongepowered.api.text")) {
                importDecl.setName("net.kyori.adventure.text");
            }
            
            // Handle missing vector package
            if (importedName.startsWith("com.flowpowered.math.vector")) {
                importDecl.setName("org.spongepowered.math.vector");
            }
            
            // Handle ClientConnectionEvent changes
            if (importedName.equals("org.spongepowered.api.event.network.ClientConnectionEvent")) {
                importDecl.setName("org.spongepowered.api.event.network.ClientConnectionEvent");
            }
            
            return super.visit(importDecl, arg);
        }
        
        @Override
        public Visitable visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            // Update class hierarchy for platforms
            NodeList<ClassOrInterfaceType> implementsList = classDecl.getImplementedTypes();
            for (int i = 0; i < implementsList.size(); i++) {
                ClassOrInterfaceType type = implementsList.get(i);
                if (type.getNameAsString().equals("PlatformPlugin")) {
                    // Replace PlatformPlugin<CommandSource> with Audience
                    type.setTypeArguments(NodeList.nodeList(new ClassOrInterfaceType(null, "net.kyori.adventure.audience.Audience")));
                }
            }
            
            return super.visit(classDecl, arg);
        }
        
        @Override
        public Visitable visit(MethodDeclaration methodDecl, Void arg) {
            // Update method signature for CommandSource parameters
            NodeList<com.github.javaparser.ast.body.Parameter> params = methodDecl.getParameters();
            for (int i = 0; i < params.size(); i++) {
                com.github.javaparser.ast.body.Parameter param = params.get(i);
                if (param.getType().asString().equals("CommandSource")) {
                    // Replace with Audience
                    param.setType("net.kyori.adventure.audience.Audience");
                }
            }
            
            return super.visit(methodDecl, arg);
        }
    }
}