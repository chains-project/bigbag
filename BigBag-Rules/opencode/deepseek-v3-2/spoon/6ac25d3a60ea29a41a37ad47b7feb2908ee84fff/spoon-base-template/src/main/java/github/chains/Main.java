package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }

        String sourceDir = args[0];
        System.out.println("Applying Jakarta Servlet 6.0.0 migration transformation to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setLevel("OFF");
        launcher.addInputResource(sourceDir);
        
        // Create model
        CtModel model = launcher.buildModel();
        
        int transformations = 0;
        
        // Transformation 1: Remove imports of jakarta.servlet.http.HttpSessionContext
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        for (CtImport ctImport : imports) {
            try {
                String importStr = ctImport.getReference().toString();
                if (importStr.contains("jakarta.servlet.http.HttpSessionContext") || 
                    (importStr.contains("HttpSessionContext") && importStr.contains("jakarta"))) {
                    System.out.println("Removing import: " + importStr);
                    ctImport.delete();
                    transformations++;
                }
            } catch (Exception e) {
                // Try alternative approach
                try {
                    if (ctImport.toString().contains("HttpSessionContext")) {
                        System.out.println("Removing import (fallback): " + ctImport);
                        ctImport.delete();
                        transformations++;
                    }
                } catch (Exception e2) {
                    // Skip if can't process import
                }
            }
        }
        
        // Transformation 2: Remove implementations of deprecated HttpSession methods
        List<CtClass<?>> classes = model.getElements(new TypeFilter<>(CtClass.class));
        for (CtClass<?> clazz : classes) {
            // Check if class implements HttpSession directly or indirectly
            boolean implementsHttpSession = false;
            for (CtTypeReference<?> iface : clazz.getSuperInterfaces()) {
                if (iface.getQualifiedName().equals("jakarta.servlet.http.HttpSession")) {
                    implementsHttpSession = true;
                    break;
                }
            }
            
            if (implementsHttpSession) {
                System.out.println("Processing HttpSession implementation: " + clazz.getQualifiedName());
                
                // Remove getSessionContext() method
                List<CtMethod<?>> methods = new ArrayList<>(clazz.getMethods());
                for (CtMethod<?> method : methods) {
                    String methodName = method.getSimpleName();
                    String signature = method.getSignature();
                    System.out.println("  Checking method: " + methodName + " with signature: " + signature);
                    
                    // Remove getSessionContext() method
                    if (methodName.equals("getSessionContext")) {
                        System.out.println("  Removing method: " + signature);
                        method.delete();
                        transformations++;
                    }
                    // Remove getValue(String) method  
                    else if (methodName.equals("getValue") && method.getParameters().size() == 1 && method.getParameters().get(0).getType().toString().contains("String")) {
                        System.out.println("  Removing method: " + signature);
                        method.delete();
                        transformations++;
                    }
                    // Remove getValueNames() method
                    else if (methodName.equals("getValueNames") && method.getParameters().isEmpty()) {
                        System.out.println("  Removing method: " + signature);
                        method.delete();
                        transformations++;
                    }
                    // Remove putValue(String, Object) method
                    else if (methodName.equals("putValue") && method.getParameters().size() == 2) {
                        System.out.println("  Removing method: " + signature);
                        method.delete();
                        transformations++;
                    }
                    // Remove removeValue(String) method
                    else if (methodName.equals("removeValue") && method.getParameters().size() == 1 && method.getParameters().get(0).getType().toString().contains("String")) {
                        System.out.println("  Removing method: " + signature);
                        method.delete();
                        transformations++;
                    }
                }
            }
        }
        
        // Apply transformations
        if (transformations > 0) {
            System.out.println("Applied " + transformations + " transformations");
            // Write transformations
            launcher.prettyprint();
            System.out.println("Transformations applied. Check 'spooned' directory for results.");
        } else {
            System.out.println("No transformations needed");
        }
    }
}