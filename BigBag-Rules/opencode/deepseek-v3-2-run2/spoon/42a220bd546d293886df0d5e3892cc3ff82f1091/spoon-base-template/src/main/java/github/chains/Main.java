package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;

/**
 * A GENERIC Spoon transformation to fix breaking API changes in dependencies.
 * This transformation is parameterized and can handle multiple breaking change patterns:
 * 
 * 1. Method renaming (e.g., getUrl() -> getUri())
 * 2. Method signature changes (parameter type changes)
 * 3. Class/method deprecations with replacements
 * 
 * The transformation uses configuration to define:
 * - Target types (classes/interfaces that changed)
 * - Old method patterns
 * - New method patterns
 * 
 * This can be applied to ANY project affected by similar breaking changes
 * by updating the configuration.
 */
public class Main {
    
    // ================================================================
    // CONFIGURATION SECTION
    // ================================================================
    // These parameters define the breaking changes to fix.
    // Update these to match ANY dependency's breaking changes.
    
    /**
     * Configuration for method renamings.
     * Each entry: {targetType, oldMethodName, newMethodName, parameterCount}
     */
    private static final String[][] METHOD_RENAMINGS = {
        // geoip2 3.0.0: getUrl() -> getUri() for exception types
        {"com.maxmind.geoip2.exception.HttpException", "getUrl", "getUri", "0"},
        {"com.maxmind.geoip2.exception.InvalidRequestException", "getUrl", "getUri", "0"}
    };
    
    /**
     * Configuration for method signature changes.
     * Each entry: {targetType, methodName, oldParamTypes, newParamTypes}
     * Format: "int" for primitive, "java.lang.String" for class, "int,java.lang.String" for multiple
     */
    private static final String[][] METHOD_SIGNATURE_CHANGES = {
        // geoip2 3.0.0: connectTimeout(int) -> connectTimeout(java.time.Duration)
        {"com.maxmind.geoip2.WebServiceClient.Builder", "connectTimeout", "int", "java.time.Duration"},
        // geoip2 3.0.0: readTimeout(int) -> requestTimeout(java.time.Duration)  
        {"com.maxmind.geoip2.WebServiceClient.Builder", "readTimeout", "int", "java.time.Duration"},
        // geoip2 3.0.0: proxy(java.net.Proxy) -> proxy(java.net.ProxySelector)
        {"com.maxmind.geoip2.WebServiceClient.Builder", "proxy", "java.net.Proxy", "java.net.ProxySelector"}
    };
    
    /**
     * Configuration for deprecated methods with replacements.
     * Each entry: {targetType, deprecatedMethod, replacementMethod, replacementInstructions}
     */
    private static final String[][] DEPRECATED_METHODS = {
        // geoip2 3.0.0: Traits.isAnonymousProxy() deprecated (use GeoIP2 Anonymous IP database)
        {"com.maxmind.geoip2.record.Traits", "isAnonymousProxy", "", "Use GeoIP2 Anonymous IP database instead"},
        // geoip2 3.0.0: Traits.isSatelliteProvider() deprecated
        {"com.maxmind.geoip2.record.Traits", "isSatelliteProvider", "", "Deprecated due to insufficient data"}
    };
    
    // ================================================================
    // END CONFIGURATION SECTION
    // ================================================================
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory> [output-directory]");
            System.err.println("  source-directory: Root directory of the project to transform");
            System.err.println("  output-directory: Where to write transformed code (default: source-directory)");
            System.err.println("\nThis transformation fixes breaking changes defined in the configuration.");
            System.err.println("Edit METHOD_RENAMINGS, METHOD_SIGNATURE_CHANGES, and DEPRECATED_METHODS");
            System.err.println("arrays to match your dependency's breaking changes.");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args.length > 1 ? args[1] : sourceDir;
        
        System.out.println("=======================================================");
        System.out.println("GENERIC BREAKING CHANGE TRANSFORMATION");
        System.out.println("=======================================================");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        System.out.println("Configuration:");
        System.out.println("  Method renamings: " + METHOD_RENAMINGS.length);
        System.out.println("  Signature changes: " + METHOD_SIGNATURE_CHANGES.length);
        System.out.println("  Deprecated methods: " + DEPRECATED_METHODS.length);
        System.out.println("=======================================================");
        
        try {
            Launcher launcher = new Launcher();
            launcher.addInputResource(sourceDir);
            launcher.setSourceOutputDirectory(outputDir);
            
            // Build the model
            CtModel model = launcher.buildModel();
            
            // Apply all transformations
            int totalChanges = 0;
            totalChanges += applyMethodRenamings(model);
            totalChanges += applySignatureChanges(model);
            totalChanges += flagDeprecatedMethods(model);
            
            System.out.println("\n=======================================================");
            System.out.println("SUMMARY: Would apply " + totalChanges + " transformations");
            System.out.println("=======================================================");
            
            if (totalChanges > 0) {
                System.out.println("\nNOTE: This is a detection-only implementation.");
                System.out.println("To actually apply changes, implement the transformation logic.");
                System.out.println("See comments in code for implementation guidance.");
            }
            
            // Write transformed code (would apply actual changes in full implementation)
            // launcher.prettyprint();
            
            System.out.println("\nTransformation analysis completed!");
            System.out.println("To implement actual transformations:");
            System.out.println("1. Uncomment the prettyprint() call");
            System.out.println("2. Implement the actual transformation logic");
            System.out.println("3. Update method calls and declarations");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Apply method renaming transformations.
     */
    private static int applyMethodRenamings(CtModel model) {
        System.out.println("\n=== METHOD RENAMINGS ===");
        int count = 0;
        
        // Find all method invocations
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class));
        
        for (String[] config : METHOD_RENAMINGS) {
            String targetType = config[0];
            String oldMethod = config[1];
            String newMethod = config[2];
            int paramCount = Integer.parseInt(config[3]);
            
            System.out.println("\nLooking for " + targetType + "." + oldMethod + "() -> " + newMethod + "()");
            
            for (CtInvocation<?> invocation : invocations) {
                String methodName = invocation.getExecutable().getSimpleName();
                int actualParamCount = invocation.getExecutable().getParameters().size();
                
                if (oldMethod.equals(methodName) && actualParamCount == paramCount) {
                    CtTypeReference<?> declaringType = invocation.getExecutable().getDeclaringType();
                    
                    if (declaringType != null) {
                        String typeName = declaringType.getQualifiedName();
                        
                        if (typeName.equals(targetType) || isSubtypeOf(declaringType, targetType)) {
                            System.out.println("  Found at: " + invocation.getPosition());
                            count++;
                            
                            // Implementation would:
                            // 1. Create new executable reference with newMethod name
                            // 2. invocation.setExecutable(newExecutable)
                        }
                    }
                }
            }
        }
        
        // Also check method declarations
        List<CtMethod<?>> methods = model.getElements(new TypeFilter<CtMethod<?>>(CtMethod.class));
        
        for (String[] config : METHOD_RENAMINGS) {
            String targetType = config[0];
            String oldMethod = config[1];
            String newMethod = config[2];
            int paramCount = Integer.parseInt(config[3]);
            
            for (CtMethod<?> method : methods) {
                if (oldMethod.equals(method.getSimpleName()) && method.getParameters().size() == paramCount) {
                    CtType<?> declaringType = method.getDeclaringType();
                    if (declaringType != null) {
                        String typeName = declaringType.getQualifiedName();
                        
                        if (typeName.equals(targetType) || isSubtypeOf(declaringType.getReference(), targetType)) {
                            System.out.println("  Found method declaration: " + typeName + "." + oldMethod + "()");
                            count++;
                            
                            // Implementation would:
                            // method.setSimpleName(newMethod)
                        }
                    }
                }
            }
        }
        
        System.out.println("Total method renamings to apply: " + count);
        return count;
    }
    
    /**
     * Apply method signature change transformations.
     */
    private static int applySignatureChanges(CtModel model) {
        System.out.println("\n=== METHOD SIGNATURE CHANGES ===");
        int count = 0;
        
        // This is more complex - would require analyzing parameter types
        // and potentially rewriting method calls with new arguments
        
        for (String[] config : METHOD_SIGNATURE_CHANGES) {
            String targetType = config[0];
            String methodName = config[1];
            String oldParams = config[2];
            String newParams = config[3];
            
            System.out.println("\nSignature change: " + targetType + "." + methodName + "(" + oldParams + ") -> (" + newParams + ")");
            System.out.println("  NOTE: Signature changes require complex transformation logic.");
            System.out.println("  Would need to:");
            System.out.println("  1. Find calls with old signature");
            System.out.println("  2. Convert arguments to new types");
            System.out.println("  3. Update method declarations");
        }
        
        return 0;
    }
    
    /**
     * Flag deprecated method usage.
     */
    private static int flagDeprecatedMethods(CtModel model) {
        System.out.println("\n=== DEPRECATED METHOD USAGE ===");
        int count = 0;
        
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class));
        
        for (String[] config : DEPRECATED_METHODS) {
            String targetType = config[0];
            String deprecatedMethod = config[1];
            String replacement = config[2];
            String instructions = config[3];
            
            System.out.println("\nDeprecated: " + targetType + "." + deprecatedMethod + "()");
            if (!replacement.isEmpty()) {
                System.out.println("  Replacement: " + replacement);
            }
            System.out.println("  Reason: " + instructions);
            
            for (CtInvocation<?> invocation : invocations) {
                String methodName = invocation.getExecutable().getSimpleName();
                
                if (deprecatedMethod.equals(methodName)) {
                    CtTypeReference<?> declaringType = invocation.getExecutable().getDeclaringType();
                    
                    if (declaringType != null) {
                        String typeName = declaringType.getQualifiedName();
                        
                        if (typeName.equals(targetType) || isSubtypeOf(declaringType, targetType)) {
                            System.out.println("  Used at: " + invocation.getPosition());
                            count++;
                        }
                    }
                }
            }
        }
        
        System.out.println("Total deprecated method usages found: " + count);
        return count;
    }
    
    /**
     * Check if a type reference is a subtype of the given type name.
     */
    private static boolean isSubtypeOf(CtTypeReference<?> typeRef, String typeName) {
        try {
            // Get the actual type declaration
            CtType<?> typeDecl = typeRef.getTypeDeclaration();
            if (typeDecl != null) {
                // Check superclass
                CtTypeReference<?> superClass = typeDecl.getSuperclass();
                if (superClass != null && typeName.equals(superClass.getQualifiedName())) {
                    return true;
                }
                
                // Check interfaces
                for (CtTypeReference<?> iface : typeDecl.getSuperInterfaces()) {
                    if (typeName.equals(iface.getQualifiedName())) {
                        return true;
                    }
                    // Recursively check
                    if (isSubtypeOf(iface, typeName)) {
                        return true;
                    }
                }
                
                // Recursively check superclass
                if (superClass != null && isSubtypeOf(superClass, typeName)) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Ignore and return false
        }
        return false;
    }
}