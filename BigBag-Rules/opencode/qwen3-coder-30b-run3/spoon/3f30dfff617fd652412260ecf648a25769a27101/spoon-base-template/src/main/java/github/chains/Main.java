package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic transformation tool to identify Jakarta Interceptor API references
 * that need to be updated from javax.interceptor to jakarta.interceptor.
 * 
 * This tool provides analysis of the codebase to identify migration targets
 * but does not actually modify files. It helps developers identify what needs
 * to be fixed during Jakarta EE migration.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -cp <classpath> github.chains.Main <source_directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        
        System.out.println("=== Jakarta Interceptor API Migration Analysis ===");
        System.out.println("Analyzing code in: " + sourceDirectory);
        System.out.println();
        
        try {
            // Create Spoon launcher
            Launcher launcher = new Launcher();
            launcher.addInputResource(sourceDirectory);
            launcher.getEnvironment().setComplianceLevel(11);
            
            // Build model
            CtModel model = launcher.buildModel();
            
            // Find all javax.interceptor references
            List<CtTypeReference<?>> references = model.getElements(new TypeFilter<>(CtTypeReference.class) {
                @Override
                public boolean matches(CtTypeReference<?> element) {
                    try {
                        String qualifiedName = element.getQualifiedName();
                        return qualifiedName != null && qualifiedName.startsWith("javax.interceptor");
                    } catch (Exception e) {
                        return false;
                    }
                }
            });
            
            System.out.println("Found " + references.size() + " javax.interceptor references:");
            
            // Report on each reference
            for (CtTypeReference<?> ref : references) {
                try {
                    String qualifiedName = ref.getQualifiedName();
                    CtType<?> parentType = ref.getParent(CtType.class);
                    String parentName = (parentType != null) ? parentType.getQualifiedName() : "unknown";
                    System.out.println("- " + qualifiedName + " (in " + parentName + ")");
                } catch (Exception e) {
                    System.out.println("- <unable to resolve reference>");
                }
            }
            
            // Also check imports
            List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
            System.out.println("\nChecking imports:");
            boolean foundImport = false;
            for (CtImport imp : imports) {
                try {
                    if (imp.getReference() != null) {
                        String importStr = imp.getReference().toString();
                        if (importStr.contains("javax.interceptor")) {
                            CtType<?> parentType = imp.getParent(CtType.class);
                            String parentName = (parentType != null) ? parentType.getQualifiedName() : "unknown";
                            System.out.println("- Import: " + importStr + " (in " + parentName + ")");
                            foundImport = true;
                        }
                    }
                } catch (Exception e) {
                    // Skip problematic imports
                }
            }
            
            if (!foundImport) {
                System.out.println("- No javax.interceptor imports found");
            }
            
            System.out.println("\n=== Migration Instructions ===");
            System.out.println("To migrate from javax.interceptor to jakarta.interceptor:");
            System.out.println("1. Replace all imports from \"javax.interceptor\" to \"jakarta.interceptor\"");
            System.out.println("2. Update all type references from \"javax.interceptor\" to \"jakarta.interceptor\"");
            System.out.println("3. Example: javax.interceptor.Interceptor -> jakarta.interceptor.Interceptor");
            
        } catch (Exception e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
}