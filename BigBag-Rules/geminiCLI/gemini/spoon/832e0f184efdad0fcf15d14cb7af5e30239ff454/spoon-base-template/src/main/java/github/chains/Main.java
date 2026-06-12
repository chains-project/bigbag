package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.compiler.FileSystemFolder;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Main {
    public static void main(String[] args) {
        System.out.println("Starting Spoon transformation...");
        Launcher launcher = new Launcher();
        
        // Add source folder
        String sourcePath = "/workspace/WorldwideChat/src/main/java";
        launcher.addInputResource(new FileSystemFolder(sourcePath));
        
        // Set no classpath mode
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(true); // Preserve comments
        launcher.getEnvironment().setAutoImports(false); // Disable auto-imports
        
        // Use Sniper printer to preserve formatting and imports
        launcher.getEnvironment().setPrettyPrinterCreator(() -> new SniperJavaPrettyPrinter(launcher.getEnvironment()));
        
        // Build model
        System.out.println("Building model...");
        CtModel model = launcher.buildModel();
        
        // Find invocations of parseEnchantment
        List<CtInvocation> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));
        
        int count = 0;
        Set<CtType<?>> modifiedTypes = new HashSet<>();
        
        for (CtInvocation invocation : invocations) {
            if ("parseEnchantment".equals(invocation.getExecutable().getSimpleName())) {
                System.out.println("Found usage at: " + invocation.getPosition());
                // Replace with getEnchant
                invocation.getExecutable().setSimpleName("getEnchant");
                count++;
                
                // Track modified type
                CtType<?> type = invocation.getParent(CtType.class);
                if (type != null) {
                    modifiedTypes.add(type);
                }
            }
        }
        
        System.out.println("Replaced " + count + " invocations.");
        System.out.println("Modified " + modifiedTypes.size() + " types.");
        
        if (count > 0) {
            // Only write modified types
            for (CtType<?> type : modifiedTypes) {
                try {
                    // Get original file
                    if (type.getPosition() != null && type.getPosition().getFile() != null) {
                         File originalFile = type.getPosition().getFile();
                         System.out.println("Writing modified file: " + originalFile.getAbsolutePath());
                         
                         // Get compilation unit to print full file (package, imports, type)
                         spoon.reflect.declaration.CtCompilationUnit cu = type.getFactory().CompilationUnit().getOrCreate(type);
                         // Or try to get it from position if possible, but getOrCreate is safer if we just want to print
                         
                         // Wait, getOrCreate might return empty CU if it's not linked?
                         // If type is part of model, it should have a CU.
                         // But we want the CU that contains this type.
                         
                         if (type.getPosition().getCompilationUnit() != null) {
                             cu = type.getPosition().getCompilationUnit();
                         }
                         
                         try (FileWriter writer = new FileWriter(originalFile)) {
                            writer.write(cu.prettyprint());
                         }
                    } else {
                        System.out.println("Warning: Could not determine file for type " + type.getQualifiedName());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("Transformation applied.");
        } else {
            System.out.println("No changes made.");
        }
    }
}
