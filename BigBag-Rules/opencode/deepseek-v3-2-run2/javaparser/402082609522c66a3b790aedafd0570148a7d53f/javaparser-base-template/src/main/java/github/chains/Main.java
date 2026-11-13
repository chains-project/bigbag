package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        try {
            Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.toString().endsWith(".java")) {
                        processJavaFile(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            System.out.println("Transformation completed successfully!");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processJavaFile(Path file) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(file).getResult().orElseThrow(() -> 
                new RuntimeException("Failed to parse " + file));
            
            SpongeAPI8MigrationVisitor visitor = new SpongeAPI8MigrationVisitor();
            cu.accept(visitor, null);
            
            if (visitor.isModified()) {
                Files.write(file, cu.toString().getBytes());
                System.out.println("Updated: " + file);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + file + ": " + e.getMessage());
        }
    }
    
    static class SpongeAPI8MigrationVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean isModified() {
            return modified;
        }
        
        @Override
        public Node visit(ImportDeclaration n, Void arg) {
            String importName = n.getNameAsString();
            
            // Command system imports
            if (importName.equals("org.spongepowered.api.command.CommandSource")) {
                modified = true;
                return new ImportDeclaration(new Name("org.spongepowered.api.command.CommandCause"), false, false);
            }
            if (importName.equals("org.spongepowered.api.command.CommandManager")) {
                modified = true;
                return new ImportDeclaration(new Name("org.spongepowered.api.command.manager.CommandManager"), false, false);
            }
            if (importName.equals("org.spongepowered.api.command.CommandException")) {
                modified = true;
                return new ImportDeclaration(new Name("org.spongepowered.api.command.exception.CommandException"), false, false);
            }
            
            // Event system imports
            if (importName.equals("org.spongepowered.api.event.network.ClientConnectionEvent")) {
                modified = true;
                return new ImportDeclaration(new Name("org.spongepowered.api.event.network.ServerSideConnectionEvent"), false, false);
            }
            if (importName.startsWith("org.spongepowered.api.event.game.state")) {
                modified = true;
                return null; // Remove old lifecycle events
            }
            
            // Network system imports
            if (importName.equals("org.spongepowered.api.network.ChannelRegistrar")) {
                modified = true;
                return new ImportDeclaration(new Name("org.spongepowered.api.network.channel.ChannelManager"), false, false);
            }
            if (importName.equals("org.spongepowered.api.network.ChannelBinding.RawDataChannel")) {
                modified = true;
                return new ImportDeclaration(new Name("org.spongepowered.api.network.channel.raw.RawDataChannel"), false, false);
            }
            if (importName.equals("org.spongepowered.api.network.ChannelBuf")) {
                modified = true;
                return new ImportDeclaration(new Name("org.spongepowered.api.network.channel.ChannelBuf"), false, false);
            }
            if (importName.equals("org.spongepowered.api.network.RawDataListener")) {
                modified = true;
                return new ImportDeclaration(new Name("org.spongepowered.api.network.channel.raw.RawDataChannel"), false, false);
            }
            
            // Plugin system imports
            if (importName.equals("org.spongepowered.api.plugin.Plugin")) {
                modified = true;
                return new ImportDeclaration(new Name("org.spongepowered.plugin.PluginContainer"), false, false);
            }
            
            // Text system imports
            if (importName.equals("org.spongepowered.api.text.serializer.TextSerializers")) {
                modified = true;
                return null; // Remove - use Adventure components directly
            }
            if (importName.equals("org.spongepowered.api.text.Text")) {
                modified = true;
                return null; // Remove - use Adventure components directly
            }
            
            // Command args/spec imports
            if (importName.startsWith("org.spongepowered.api.command.args") || 
                importName.startsWith("org.spongepowered.api.command.spec")) {
                modified = true;
                return null; // Remove old command system
            }
            
            return n;
        }
        
        @Override
        public Node visit(ClassOrInterfaceType n, Void arg) {
            String typeName = n.getNameAsString();
            
            // Replace CommandSource with CommandCause in type declarations
            if (typeName.equals("CommandSource")) {
                modified = true;
                return new ClassOrInterfaceType("CommandCause");
            }
            
            return n;
        }
        
        @Override
        public Node visit(MethodDeclaration n, Void arg) {
            // Update method parameter types from CommandSource to CommandCause
            n.getParameters().forEach(param -> {
                if (param.getType().toString().equals("CommandSource")) {
                    modified = true;
                    param.setType(new ClassOrInterfaceType("CommandCause"));
                }
            });
            
            // Update method return types
            if (n.getType().toString().equals("CommandSource")) {
                modified = true;
                n.setType(new ClassOrInterfaceType("CommandCause"));
            }
            
            return n;
        }
        
        @Override
        public Node visit(ClassOrInterfaceDeclaration n, Void arg) {
            // Update class/interface implementations that use CommandSource
            if (n.getImplementedTypes() != null) {
                n.getImplementedTypes().forEach(type -> {
                    if (type.getNameAsString().equals("CommandSource")) {
                        modified = true;
                        type.setName("CommandCause");
                    }
                });
            }
            
            return n;
        }
    }
}