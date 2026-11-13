package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.sniper.SniperJavaPrettyPrinter;
import java.util.List;
import java.io.File;
import java.util.Map;
import java.util.HashMap;

public class Main {
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: java Main <sourceDirectory> <outputDirectory> [signaturePairs...]");
            System.err.println("Example: java Main /path/to/src /path/to/out");
            System.err.println("  \"org.hamcrest.core.StringContains(boolean,java.lang.String):org.hamcrest.core.StringContains(java.lang.String)\"");
            System.err.println("  \"org.hamcrest.core.StringStartsWith(boolean,java.lang.String):org.hamcrest.core.StringStartsWith(java.lang.String)\"");
            System.err.println("Each signature pair is: oldSignature:newSignature");
            System.err.println("Signatures format: FullyQualifiedClassName(param1Type,param2Type,...)");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        String outputDirectory = args[1];
        
        System.out.println("Transforming source directory: " + sourceDirectory);
        System.out.println("Output directory: " + outputDirectory);
        
        // Parse signature pairs
        Map<String, SignatureTransform> transformations = new HashMap<>();
        for (int i = 2; i < args.length; i++) {
            String[] parts = args[i].split(":");
            if (parts.length != 2) {
                System.err.println("Invalid signature pair: " + args[i]);
                System.err.println("Expected format: oldSignature:newSignature");
                System.exit(1);
            }
            
            String[] oldParts = parseSignature(parts[0]);
            String[] newParts = parseSignature(parts[1]);
            
            if (oldParts == null || newParts == null) {
                System.err.println("Invalid signature format in: " + args[i]);
                System.exit(1);
            }
            
            if (!oldParts[0].equals(newParts[0])) {
                System.err.println("Class names must match in signature pair: " + args[i]);
                System.exit(1);
            }
            
            String[] oldParams = oldParts[1].isEmpty() ? new String[0] : oldParts[1].split(",");
            String[] newParams = newParts[1].isEmpty() ? new String[0] : newParts[1].split(",");
            transformations.put(oldParts[0], new SignatureTransform(oldParams, newParams));
            System.out.println("Added transformation for " + oldParts[0]);
            System.out.println("  Old: " + parts[0]);
            System.out.println("  New: " + parts[1]);
        }
        
        if (transformations.isEmpty()) {
            System.err.println("No transformations specified");
            System.exit(1);
        }
        
        // Create launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPrettyPrinterCreator(() -> new SniperJavaPrettyPrinter(launcher.getEnvironment()));
        
        // Add input resource
        launcher.addInputResource(sourceDirectory);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Find all constructor calls
        List<CtConstructorCall<?>> constructorCalls = model.getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class));
        
        System.out.println("Found " + constructorCalls.size() + " constructor calls");
        
        int transformedCount = 0;
        
        // Transform constructor calls matching any of the old signatures
        for (CtConstructorCall<?> constructorCall : constructorCalls) {
            String actualClassName = constructorCall.getType().getQualifiedName();
            SignatureTransform transform = transformations.get(actualClassName);
            
            if (transform != null) {
                // Check if number of arguments matches old signature
                if (constructorCall.getArguments().size() == transform.oldParams.length) {
                    System.out.println("Found matching constructor call: " + constructorCall);
                    
                    // Apply transformation
                    if (applyTransformation(constructorCall, transform)) {
                        transformedCount++;
                    }
                } else {
                    System.out.println("Argument count mismatch: expected " + transform.oldParams.length + ", got " + constructorCall.getArguments().size());
                }
            }
        }
        
        System.out.println("Transformed " + transformedCount + " constructor calls");
        
        // Set output directory and generate code
        launcher.setSourceOutputDirectory(outputDirectory);
        launcher.prettyprint();
        
        System.out.println("Transformation complete!");
    }
    
    private static boolean applyTransformation(CtConstructorCall<?> call, SignatureTransform transform) {
        System.out.println("  Old params count: " + transform.oldParams.length + ", New params count: " + transform.newParams.length);
        System.out.println("  Call has " + call.getArguments().size() + " arguments");
        
        // For now, handle simple case: removing first N parameters where rest match
        if (transform.oldParams.length > transform.newParams.length) {
            int paramsToRemove = transform.oldParams.length - transform.newParams.length;
            boolean paramsMatch = true;
            for (int i = 0; i < transform.newParams.length; i++) {
                if (!transform.oldParams[i + paramsToRemove].equals(transform.newParams[i])) {
                    System.out.println("    Param mismatch at " + i + ": " + transform.oldParams[i + paramsToRemove] + " != " + transform.newParams[i]);
                    paramsMatch = false;
                    break;
                }
            }
            
            if (paramsMatch) {
                System.out.println("  Transforming: removing first " + paramsToRemove + " parameters");
                // Remove first N parameters
                for (int i = 0; i < paramsToRemove; i++) {
                    if (!call.getArguments().isEmpty()) {
                        call.getArguments().remove(0);
                    }
                }
                System.out.println("  After transformation, call has " + call.getArguments().size() + " arguments");
                return true;
            }
        }
        // TODO: Handle other transformation patterns
        
        System.out.println("  Warning: Could not apply transformation - pattern not supported");
        return false;
    }
    
    private static String[] parseSignature(String signature) {
        // Format: ClassName(param1,param2,...)
        int parenIndex = signature.indexOf('(');
        if (parenIndex == -1 || !signature.endsWith(")")) {
            return null;
        }
        
        String className = signature.substring(0, parenIndex);
        String params = signature.substring(parenIndex + 1, signature.length() - 1);
        
        // Handle empty parameter list
        if (params.isEmpty()) {
            return new String[] {className, ""};
        }
        
        return new String[] {className, params};
    }
    
    private static class SignatureTransform {
        String[] oldParams;
        String[] newParams;
        
        SignatureTransform(String[] oldParams, String[] newParams) {
            this.oldParams = oldParams;
            this.newParams = newParams;
        }
    }
}