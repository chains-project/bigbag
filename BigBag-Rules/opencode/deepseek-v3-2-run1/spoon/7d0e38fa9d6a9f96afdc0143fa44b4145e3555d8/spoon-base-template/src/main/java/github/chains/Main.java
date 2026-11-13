package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;

public class Main {
    
    // Configuration: Map of fully qualified method signatures to their new parameter count
    private static final Map<String, Integer> METHOD_SIGNATURES = new HashMap<>();
    
    static {
        // DNS API methods that now require location parameter
        METHOD_SIGNATURES.put("com.google.api.services.dns.Dns$ManagedZones.create(java.lang.String,com.google.api.services.dns.model.ManagedZone)", 3);
        METHOD_SIGNATURES.put("com.google.api.services.dns.Dns$ManagedZones.get(java.lang.String,java.lang.String)", 3);
        METHOD_SIGNATURES.put("com.google.api.services.dns.Dns$ManagedZones.list(java.lang.String)", 2);
        METHOD_SIGNATURES.put("com.google.api.services.dns.Dns$ManagedZones.delete(java.lang.String,java.lang.String)", 3);
        METHOD_SIGNATURES.put("com.google.api.services.dns.Dns$ResourceRecordSets.list(java.lang.String,java.lang.String)", 3);
        METHOD_SIGNATURES.put("com.google.api.services.dns.Dns$Projects.get(java.lang.String)", 2);
        METHOD_SIGNATURES.put("com.google.api.services.dns.Dns$Changes.create(java.lang.String,java.lang.String,com.google.api.services.dns.model.Change)", 4);
        METHOD_SIGNATURES.put("com.google.api.services.dns.Dns$Changes.get(java.lang.String,java.lang.String,java.lang.String)", 4);
        METHOD_SIGNATURES.put("com.google.api.services.dns.Dns$Changes.list(java.lang.String,java.lang.String)", 3);
    }
    
    // Default location value to insert
    private static final String DEFAULT_LOCATION = "global";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying transformation to: " + sourceDir);
        
        try {
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setAutoImports(true);
            launcher.addInputResource(sourceDir);
            
            CtModel model = launcher.buildModel();
            
            int transformations = applyTransformations(model);
            
            System.out.println("Applied " + transformations + " transformations");
            
            if (transformations > 0) {
                launcher.setSourceOutputDirectory(sourceDir);
                launcher.prettyprint();
                System.out.println("Transformation completed successfully");
            } else {
                System.out.println("No transformations needed");
            }
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static int applyTransformations(CtModel model) {
        int transformationCount = 0;
        
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));
        
        for (CtInvocation<?> invocation : invocations) {
            CtExecutableReference<?> execRef = invocation.getExecutable();
            if (execRef == null) continue;
            
            String signature = getMethodSignature(execRef);
            Integer expectedParamCount = METHOD_SIGNATURES.get(signature);
            
            if (expectedParamCount != null) {
                int currentParamCount = invocation.getArguments().size();
                
                if (currentParamCount == expectedParamCount - 1) {
                    // Missing location parameter, need to add it
                    if (addLocationParameter(invocation, signature, currentParamCount)) {
                        transformationCount++;
                        System.out.println("Fixed: " + signature + " at " + 
                            invocation.getPosition().getFile().getName() + ":" + 
                            invocation.getPosition().getLine());
                    }
                }
            }
        }
        
        return transformationCount;
    }
    
    private static String getMethodSignature(CtExecutableReference<?> execRef) {
        StringBuilder signature = new StringBuilder();
        
        // Get declaring type
        CtTypeReference<?> declaringType = execRef.getDeclaringType();
        if (declaringType != null) {
            signature.append(declaringType.getQualifiedName());
        }
        
        signature.append(".");
        signature.append(execRef.getSimpleName());
        
        // Get parameter types
        signature.append("(");
        List<CtTypeReference<?>> paramTypes = execRef.getParameters();
        for (int i = 0; i < paramTypes.size(); i++) {
            if (i > 0) signature.append(",");
            signature.append(paramTypes.get(i).getQualifiedName());
        }
        signature.append(")");
        
        return signature.toString();
    }
    
    private static boolean addLocationParameter(CtInvocation<?> invocation, String signature, int insertionIndex) {
        try {
            // Create a literal for the location parameter
            SpoonStandardEnvironment env = invocation.getFactory().getEnvironment();
            
            // Add "global" string literal as the location parameter
            // Based on the signature pattern, we need to insert at different positions
            if (signature.contains("ManagedZones.create")) {
                // create(project, zone) -> create(project, location, zone)
                // Insert location after project
                invocation.addArgument(insertionIndex, invocation.getFactory().createLiteral("global"));
            } else if (signature.contains("ManagedZones.get")) {
                // get(project, zone) -> get(project, location, zone)
                // Insert location after project
                invocation.addArgument(1, invocation.getFactory().createLiteral("global"));
            } else if (signature.contains("ManagedZones.list")) {
                // list(project) -> list(project, location)
                // Add location as second parameter
                invocation.addArgument(invocation.getFactory().createLiteral("global"));
            } else if (signature.contains("ManagedZones.delete")) {
                // delete(project, zone) -> delete(project, location, zone)
                // Insert location after project
                invocation.addArgument(1, invocation.getFactory().createLiteral("global"));
            } else if (signature.contains("ResourceRecordSets.list")) {
                // list(project, zone) -> list(project, location, zone)
                // Insert location after project
                invocation.addArgument(1, invocation.getFactory().createLiteral("global"));
            } else if (signature.contains("Projects.get")) {
                // get(project) -> get(project, location)
                // Add location as second parameter
                invocation.addArgument(invocation.getFactory().createLiteral("global"));
            } else if (signature.contains("Changes.create")) {
                // create(project, zone, change) -> create(project, location, zone, change)
                // Insert location after project
                invocation.addArgument(1, invocation.getFactory().createLiteral("global"));
            } else if (signature.contains("Changes.get")) {
                // get(project, zone, changeId) -> get(project, location, zone, changeId)
                // Insert location after project
                invocation.addArgument(1, invocation.getFactory().createLiteral("global"));
            } else if (signature.contains("Changes.list")) {
                // list(project, zone) -> list(project, location, zone)
                // Insert location after project
                invocation.addArgument(1, invocation.getFactory().createLiteral("global"));
            }
            
            return true;
        } catch (Exception e) {
            System.err.println("Error transforming invocation: " + signature + " - " + e.getMessage());
            return false;
        }
    }
}