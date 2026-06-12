package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class SimpleFix {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java SimpleFix <sourceFile> <outputFile>");
            System.exit(1);
        }
        
        String sourceFile = args[0];
        String outputFile = args[1];
        
        System.out.println("Fixing ManagedObject<?> compatibility in: " + sourceFile);
        
        // Read the file
        String content = Files.readString(Path.of(sourceFile));
        
        // Fix 1: List<ManagedObject> -> List<ManagedObject<?>>
        // Fix 2: SortedMap<MOScope, ManagedObject> -> SortedMap<MOScope, ManagedObject<?>>
        // Use regex to match these patterns
        String fixed = content
            .replaceAll("List<ManagedObject>", "List<ManagedObject<?>>")
            .replaceAll("SortedMap<MOScope,\\s*ManagedObject>", "SortedMap<MOScope, ManagedObject<?>>")
            .replaceAll("SortedMap<MOScope, ManagedObject>", "SortedMap<MOScope, ManagedObject<?>>");
        
        // Write the fixed file
        Files.writeString(Path.of(outputFile), fixed);
        
        System.out.println("Fixed file written to: " + outputFile);
    }
}