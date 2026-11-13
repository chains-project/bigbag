package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.visitor.ClassTypingContext;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar spoon-base.jar <source-dir> <output-dir>");
            System.err.println("  source-dir: Path to source code to transform");
            System.err.println("  output-dir: Path where transformed code will be written");
            System.exit(1);
        }

        String sourceDir = args[0];
        String outputDir = args[1];

        System.out.println("Creating Spoon transformation for SnakeYAML 1.32 breaking change...");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);

        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Define the target method signature to match
        String targetClassName = "org.yaml.snakeyaml.representer.Representer";
        String targetMethodName = "getProperties";
        String targetMethodSignature = "getProperties(java.lang.Class)";
        
        System.out.println("Looking for methods overriding: " + targetClassName + "." + targetMethodName);
        
        // Find all classes in the model
        List<CtClass<?>> allClasses = model.getElements(new TypeFilter<CtClass<?>>(CtClass.class));
        
        int transformationCount = 0;
        
        for (CtClass<?> ctClass : allClasses) {
            // Check if this class extends Representer
            if (extendsClass(ctClass, targetClassName)) {
                System.out.println("Found class extending " + targetClassName + ": " + ctClass.getQualifiedName());
                
                System.out.println("  Checking all methods in class for IntrospectionException...");
                
                // Check ALL methods in this class for IntrospectionException
                // Any method that throws IntrospectionException because it calls super.getProperties
                // should no longer need it since getProperties no longer throws it
                for (CtMethod<?> method : ctClass.getMethods()) {
                    Set<CtTypeReference<? extends Throwable>> thrownTypes = method.getThrownTypes();
                    List<CtTypeReference<? extends Throwable>> introspectionExceptions = thrownTypes.stream()
                        .filter(ref -> ref.getQualifiedName().equals("java.beans.IntrospectionException"))
                        .collect(Collectors.toList());
                    
                    if (!introspectionExceptions.isEmpty()) {
                        System.out.println("    Removing IntrospectionException from method: " + method.getSignature());
                        
                        // Remove IntrospectionException from throws clause
                        for (CtTypeReference<? extends Throwable> exceptionRef : introspectionExceptions) {
                            method.removeThrownType(exceptionRef);
                        }
                    
                        transformationCount++;
                    }
                }
            }
        }
        
        System.out.println("\nTransformations applied: " + transformationCount);
        
        if (transformationCount > 0) {
            // Write transformed code
            launcher.setSourceOutputDirectory(outputDir);
            launcher.prettyprint();
            System.out.println("Transformed code written to: " + outputDir);
        } else {
            System.out.println("No transformations needed.");
        }
    }
    
    private static boolean extendsClass(CtClass<?> ctClass, String targetClassName) {
        CtTypeReference<?> superclass = ctClass.getSuperclass();
        if (superclass != null && superclass.getQualifiedName().equals(targetClassName)) {
            return true;
        }
        
        // Check all superclasses recursively
        while (superclass != null) {
            if (superclass.getQualifiedName().equals(targetClassName)) {
                return true;
            }
            try {
                CtType<?> superType = superclass.getTypeDeclaration();
                if (superType instanceof CtClass) {
                    superclass = ((CtClass<?>) superType).getSuperclass();
                } else {
                    break;
                }
            } catch (Exception e) {
                // If we can't resolve the type, break
                break;
            }
        }
        
        return false;
    }
}