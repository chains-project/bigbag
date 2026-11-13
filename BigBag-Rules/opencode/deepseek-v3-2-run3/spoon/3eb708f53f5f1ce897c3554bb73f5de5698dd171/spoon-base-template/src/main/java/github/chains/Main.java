package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar spoon-transformation.jar <input-source-dir> <output-dir>");
            System.err.println("This is a generic API migration transformation.");
            System.err.println("Configure the mapping in the APIMigrationProcessor class.");
            return;
        }

        String inputPath = args[0];
        String outputPath = args[1];

        Launcher launcher = new Launcher();
        launcher.addInputResource(inputPath);
        launcher.setSourceOutputDirectory(outputPath);
        
        // Exclude problematic directories (like generated samples that cause duplicate class issues)
        launcher.getEnvironment().setNoClasspath(true); // Don't require full classpath
        launcher.getEnvironment().setAutoImports(true); // Auto-add imports when needed
        
        // Add the generic API migration processor
        launcher.addProcessor(new APIMigrationProcessor());
        
        try {
            // Run the transformation
            launcher.run();
            System.out.println("Transformation completed. Output written to: " + outputPath);
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Generic processor for API migration patterns.
     * This transformation handles:
     * 1. Package/namespace changes
     * 2. Class renames  
     * 3. Method signature updates
     * 
     * Configuration: Update the MAPPINGS constants to define your specific migration.
     */
    static class APIMigrationProcessor extends AbstractProcessor<CtClass<?>> {
        
        // ============================================================
        // CONFIGURE THESE MAPPINGS FOR YOUR SPECIFIC API MIGRATION
        // ============================================================
        
        /**
         * Map of old package prefixes to new package prefixes.
         * Format: "old.package.prefix" -> "new.package.prefix"
         */
        private static final Map<String, String> PACKAGE_MAPPINGS = new HashMap<>();
        static {
            // Example: Google Translate v2 to v3
            PACKAGE_MAPPINGS.put("com.google.api.services.translate.model", 
                                 "com.google.api.services.translate.v3.model");
            // Add more package mappings as needed
        }
        
        /**
         * Map of old class names to new class names.
         * These are applied when the class is found in any of the old packages.
         * Format: "OldClassName" -> "NewClassName"
         */
        private static final Map<String, String> CLASS_MAPPINGS = new HashMap<>();
        static {
            // Example: Google Translate v2 to v3 class renames
            CLASS_MAPPINGS.put("LanguagesResource", "SupportedLanguage");
            CLASS_MAPPINGS.put("TranslationsResource", "Translation");
            CLASS_MAPPINGS.put("DetectionsResourceItems", "DetectedLanguage");
            // Add more class mappings as needed
        }
        
        /**
         * Map of old method names to new method names.
         * These are applied to method invocations.
         * Format: "oldMethodName" -> "newMethodName"
         */
        private static final Map<String, String> METHOD_MAPPINGS = new HashMap<>();
        static {
            // Example: Google Translate v2 to v3 method renames
            METHOD_MAPPINGS.put("getLanguage", "getLanguageCode");
            METHOD_MAPPINGS.put("getName", "getDisplayName");
            METHOD_MAPPINGS.put("getDetectedSourceLanguage", "getDetectedLanguageCode");
            // Add more method mappings as needed
        }
        
        // ============================================================
        // END OF CONFIGURATION
        // ============================================================
        
        @Override
        public void process(CtClass<?> ctClass) {
            // Process all type references in the class
            processTypeReferences(ctClass);
            
            // Process all method invocations
            processMethodInvocations(ctClass);
            
            // Process all field accesses (optional, for field renames)
            processFieldAccesses(ctClass);
        }
        
        private void processTypeReferences(CtClass<?> ctClass) {
            // Get all type references in the class
            ctClass.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class))
                .forEach(this::updateTypeReference);
        }
        
        private void updateTypeReference(CtTypeReference<?> typeRef) {
            String qualifiedName = typeRef.getQualifiedName();
            
            // Check if this type is in any of the old packages we're migrating
            for (Map.Entry<String, String> packageMapping : PACKAGE_MAPPINGS.entrySet()) {
                String oldPackage = packageMapping.getKey();
                String newPackage = packageMapping.getValue();
                
                if (qualifiedName.startsWith(oldPackage + ".")) {
                    String simpleName = typeRef.getSimpleName();
                    
                    // Check if this class needs to be renamed
                    String newSimpleName = CLASS_MAPPINGS.get(simpleName);
                    if (newSimpleName != null) {
                        // Create new type reference with renamed class
                        String newQualifiedName = qualifiedName.replace(
                            oldPackage, newPackage).replace(simpleName, newSimpleName);
                        CtTypeReference<?> newTypeRef = getFactory().Type()
                            .createReference(newQualifiedName);
                        
                        // Replace the old type reference with the new one
                        typeRef.replace(newTypeRef);
                    } else {
                        // Just update the package for other classes in the old package
                        String newQualifiedName = qualifiedName.replace(oldPackage, newPackage);
                        CtTypeReference<?> newTypeRef = getFactory().Type()
                            .createReference(newQualifiedName);
                        typeRef.replace(newTypeRef);
                    }
                    // Break after first match (assumes no overlapping package prefixes)
                    break;
                }
            }
        }
        
        private void processMethodInvocations(CtClass<?> ctClass) {
            ctClass.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class))
                .forEach(this::updateMethodInvocation);
        }
        
        private void updateMethodInvocation(CtInvocation<?> invocation) {
            String methodName = invocation.getExecutable().getSimpleName();
            
            // Check if this method needs to be renamed
            String newMethodName = METHOD_MAPPINGS.get(methodName);
            if (newMethodName != null) {
                // Update the method name in the invocation
                invocation.getExecutable().setSimpleName(newMethodName);
            }
        }
        
        private void processFieldAccesses(CtClass<?> ctClass) {
            ctClass.getElements(new TypeFilter<CtFieldAccess<?>>(CtFieldAccess.class))
                .forEach(this::updateFieldAccess);
        }
        
        private void updateFieldAccess(CtFieldAccess<?> fieldAccess) {
            // Optional: Handle field renames if needed
            // This is useful when getter/setter patterns change
            CtTypeReference<?> targetType = fieldAccess.getVariable().getType();
            if (targetType != null) {
                String qualifiedName = targetType.getQualifiedName();
                
                // Check if this field is on a type from an old package
                for (String oldPackage : PACKAGE_MAPPINGS.keySet()) {
                    if (qualifiedName.startsWith(oldPackage + ".")) {
                        String fieldName = fieldAccess.getVariable().getSimpleName();
                        // If field names follow getter patterns, we might rename them
                        // This is project-specific and may not always be needed
                        break;
                    }
                }
            }
        }
        
        
        
        @Override
        public boolean isToBeProcessed(CtClass<?> candidate) {
            // Skip generated sample/snippet files that cause duplicate class issues
            String className = candidate.getQualifiedName();
            if (className != null && (className.contains("samples") || className.contains("snippets"))) {
                return false;
            }
            // Process all other classes to ensure we catch all API usage
            return true;
        }
        
        // Utility methods
        private String capitalize(String str) {
            if (str == null || str.isEmpty()) {
                return str;
            }
            return str.substring(0, 1).toUpperCase() + str.substring(1);
        }
        
        private String decapitalize(String str) {
            if (str == null || str.isEmpty()) {
                return str;
            }
            return str.substring(0, 1).toLowerCase() + str.substring(1);
        }
    }
}