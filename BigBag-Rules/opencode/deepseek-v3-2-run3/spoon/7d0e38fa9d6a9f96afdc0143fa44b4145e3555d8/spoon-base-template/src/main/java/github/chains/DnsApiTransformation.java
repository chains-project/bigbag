package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

/**
 * Generic Spoon transformation for DNS API breaking changes.
 * 
 * The transformation handles method signature changes where:
 * 1. Methods gain a new "location" parameter (default value: "global")
 * 2. The new parameter is inserted at position 1 (second parameter)
 * 
 * This transformation is reusable for any project affected by the same
 * DNS API breaking change.
 */
public class DnsApiTransformation {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java DnsApiTransformation <source-directory>");
            System.err.println("Example: java DnsApiTransformation /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying DNS API transformation to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        
        // We need classpath to resolve types for accurate matching
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Transformation rules: method signature -> transformation to apply
        Map<String, Transformation> transformations = new HashMap<>();
        
        // DNS API breaking changes - all need "global" as location parameter
        transformations.put("com.google.api.services.dns.Dns$ManagedZones.create(String,ManagedZone)", 
                           new Transformation(1, "global"));
        transformations.put("com.google.api.services.dns.Dns$ManagedZones.get(String,String)", 
                           new Transformation(1, "global"));
        transformations.put("com.google.api.services.dns.Dns$ManagedZones.list(String)", 
                           new Transformation(1, "global"));
        transformations.put("com.google.api.services.dns.Dns$ManagedZones.delete(String,String)", 
                           new Transformation(1, "global"));
        transformations.put("com.google.api.services.dns.Dns$ResourceRecordSets.list(String,String)", 
                           new Transformation(1, "global"));
        transformations.put("com.google.api.services.dns.Dns$Changes.create(String,String,Change)", 
                           new Transformation(1, "global"));
        transformations.put("com.google.api.services.dns.Dns$Changes.get(String,String,String)", 
                           new Transformation(1, "global"));
        transformations.put("com.google.api.services.dns.Dns$Changes.list(String,String)", 
                           new Transformation(1, "global"));
        
        // Special case: Projects.get(String) -> Projects.get(String,String) (duplicate param)
        transformations.put("com.google.api.services.dns.Dns$Projects.get(String)", 
                           new Transformation(1, "arg0"));
        
        int totalTransformed = 0;
        
        // Process all method invocations
        for (CtInvocation<?> invocation : model.getElements(new TypeFilter<>(CtInvocation.class))) {
            try {
                String signature = getSimpleSignature(invocation);
                
                if (transformations.containsKey(signature)) {
                    Transformation rule = transformations.get(signature);
                    if (applyTransformation(invocation, rule)) {
                        totalTransformed++;
                        System.out.println("✓ Transformed: " + signature + 
                                         " at " + getLocation(invocation));
                    }
                }
            } catch (Exception e) {
                System.err.println("Error transforming invocation: " + e.getMessage());
            }
        }
        
        System.out.println("\nTotal transformations applied: " + totalTransformed);
        
        if (totalTransformed > 0) {
            // Output transformed code to same directory
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            System.out.println("Transformed code written to: " + sourceDir);
        } else {
            System.out.println("No transformations needed or no matching patterns found.");
            System.out.println("Make sure the code uses the old DNS API signatures.");
        }
    }
    
    private static String getSimpleSignature(CtInvocation<?> invocation) {
        if (invocation.getExecutable() == null) {
            return "";
        }
        
        String className = invocation.getExecutable().getDeclaringType().getSimpleName();
        String methodName = invocation.getExecutable().getSimpleName();
        
        StringBuilder sig = new StringBuilder();
        sig.append(className).append(".").append(methodName).append("(");
        
        List<CtTypeReference<?>> params = invocation.getExecutable().getParameters();
        if (params != null) {
            for (int i = 0; i < params.size(); i++) {
                if (i > 0) sig.append(",");
                // Use simple type names for matching
                String typeName = params.get(i).getSimpleName();
                sig.append(typeName);
            }
        }
        sig.append(")");
        
        return sig.toString();
    }
    
    private static boolean applyTransformation(CtInvocation<?> invocation, Transformation rule) {
        List<CtExpression<?>> args = new ArrayList<>(invocation.getArguments());
        
        CtExpression<?> newArg;
        if ("arg0".equals(rule.value)) {
            // Duplicate first argument
            if (args.isEmpty()) {
                return false;
            }
            newArg = args.get(0).clone();
        } else {
            // String literal
            newArg = invocation.getFactory().createLiteral(rule.value);
        }
        
        // Insert at specified position
        if (rule.position >= 0 && rule.position <= args.size()) {
            args.add(rule.position, newArg);
            invocation.setArguments(args);
            return true;
        }
        
        return false;
    }
    
    private static String getLocation(CtInvocation<?> invocation) {
        return invocation.getPosition().getFile().getName() + 
               ":" + invocation.getPosition().getLine();
    }
    
    static class Transformation {
        int position;    // Where to insert the new parameter (0-based)
        String value;    // Value to insert ("global" or "arg0" for duplicate)
        
        Transformation(int position, String value) {
            this.position = position;
            this.value = value;
        }
    }
}