package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.reference.CtExecutableReference;
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
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        // launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Add transformation processors
        launcher.addProcessor(new SnakeYaml20ConstructorProcessor());
        launcher.addProcessor(new SnakeYaml20ClassProcessor());
        launcher.addProcessor(new DebugProcessor());
        
        // Set output directory
        launcher.setSourceOutputDirectory("/tmp/spoon-output");
        
        System.out.println("Starting Spoon transformation...");
        
        // Run transformation
        try {
            // First build the model
            System.out.println("Building model...");
            launcher.buildModel();
            System.out.println("Model built, running processors...");
            launcher.process();
            System.out.println("Writing output...");
            launcher.prettyprint();
            System.out.println("Spoon transformation completed.");
        } catch (Exception e) {
            System.err.println("Error running Spoon transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Processor for constructor calls (including super() calls)
    static class SnakeYaml20ConstructorProcessor extends AbstractProcessor<CtConstructorCall<?>> {
        
        @Override
        public void process(CtConstructorCall<?> constructorCall) {
            System.out.println("Processing constructor call: " + constructorCall);
            
            CtExecutableReference<?> execRef = constructorCall.getExecutable();
            if (execRef != null) {
                CtTypeReference<?> declaringType = execRef.getDeclaringType();
                
                if (declaringType != null) {
                    String typeName = declaringType.getQualifiedName();
                    System.out.println("Constructor of type: " + typeName);
                    
                    // Check if this is a constructor call to snakeyaml Constructor or BaseConstructor
                    if ("org.yaml.snakeyaml.constructor.Constructor".equals(typeName)) {
                        List<CtExpression<?>> arguments = constructorCall.getArguments();
                        System.out.println("Number of arguments: " + arguments.size());
                        
                        // Transform based on number of arguments
                        if (arguments.size() == 1 || arguments.size() == 0) {
                            transformConstructorCall(constructorCall);
                        }
                    }
                }
            }
        }
        
        private void transformConstructorCall(CtConstructorCall<?> constructorCall) {
            System.out.println(">>> Transforming constructor call at: " + constructorCall.getPosition());
            
            List<CtExpression<?>> existingArgs = constructorCall.getArguments();
            List<CtExpression<?>> newArgs = new ArrayList<>(existingArgs);
            
            // Create type reference for LoaderOptions
            CtTypeReference<?> loaderOptionsType = getFactory().Type().createReference(
                "org.yaml.snakeyaml.LoaderOptions");
            
            // Create constructor call for new LoaderOptions()
            CtConstructorCall<?> loaderOptionsConstructor = getFactory().createConstructorCall(
                loaderOptionsType);
            
            // Add LoaderOptions as the last argument
            newArgs.add(loaderOptionsConstructor);
            
            // Update the constructor call with new arguments
            constructorCall.setArguments(newArgs);
            
            System.out.println("Successfully transformed constructor call");
        }
    }
    
    // Processor for classes extending Constructor to handle super() calls in constructors
    static class SnakeYaml20ClassProcessor extends AbstractProcessor<CtClass<?>> {
        
        @Override
        public void process(CtClass<?> ctClass) {
            // Check if this class extends org.yaml.snakeyaml.constructor.Constructor
            CtTypeReference<?> superClass = ctClass.getSuperclass();
            if (superClass != null && 
                "org.yaml.snakeyaml.constructor.Constructor".equals(superClass.getQualifiedName())) {
                
                System.out.println(">>> Processing class extending Constructor: " + ctClass.getQualifiedName());
                
                // Look at all constructors of this class
                for (CtConstructor<?> constructor : ctClass.getConstructors()) {
                    // Try to find and transform super constructor calls
                    transformConstructorSuperCall(constructor);
                }
            }
        }
        
        private void transformConstructorSuperCall(CtConstructor<?> constructor) {
            System.out.println("=== DEBUG: Checking constructor " + constructor.getSimpleName() + " ===");
            
            // Get ALL elements in the constructor, not just constructor calls
            // Note: getElements(null) doesn't work with generic type inference
            // Let's just check for constructor calls specifically
            
            // Get all constructor calls in this constructor
            List<CtConstructorCall<?>> constructorCalls = 
                constructor.getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class));
            System.out.println("Constructor calls found: " + constructorCalls.size());
            
            for (CtConstructorCall<?> call : constructorCalls) {
                System.out.println("Found constructor call: " + call);
                System.out.println("  Call class: " + call.getClass().getName());
                System.out.println("  Call toString: " + call.toString());
                
                // Check if this is a super() call
                // In Spoon, super calls might be represented differently
                String callStr = call.toString();
                if (callStr.startsWith("super") || callStr.startsWith("this")) {
                    System.out.println("Found super/this call: " + callStr);
                    
                    // Check if this is calling the super constructor
                    CtExecutableReference<?> execRef = call.getExecutable();
                    if (execRef != null) {
                        CtTypeReference<?> declaringType = execRef.getDeclaringType();
                        if (declaringType != null) {
                            System.out.println("  Declaring type: " + declaringType.getQualifiedName());
                            if ("org.yaml.snakeyaml.constructor.Constructor".equals(declaringType.getQualifiedName())) {
                                System.out.println(">>> Found super call to Constructor");
                                transformSuperConstructorCall(call);
                            }
                        }
                    }
                }
            }
        }
        
        private void transformSuperConstructorCall(CtConstructorCall<?> superCall) {
            System.out.println(">>> Transforming super constructor call at: " + superCall.getPosition());
            
            List<CtExpression<?>> existingArgs = superCall.getArguments();
            List<CtExpression<?>> newArgs = new ArrayList<>(existingArgs);
            
            // Create type reference for LoaderOptions
            CtTypeReference<?> loaderOptionsType = getFactory().Type().createReference(
                "org.yaml.snakeyaml.LoaderOptions");
            
            // Create constructor call for new LoaderOptions()
            CtConstructorCall<?> loaderOptionsConstructor = getFactory().createConstructorCall(
                loaderOptionsType);
            
            // Add LoaderOptions as the last argument
            newArgs.add(loaderOptionsConstructor);
            
            // Update the super call with new arguments
            superCall.setArguments(newArgs);
            
            // Also need to add import for LoaderOptions if not already present
            CtClass<?> containingClass = superCall.getParent(CtClass.class);
            if (containingClass != null) {
                addLoaderOptionsImport(containingClass);
            }
            
            System.out.println("Successfully transformed super constructor call");
        }
        
        private void addLoaderOptionsImport(CtClass<?> ctClass) {
            // Check if import already exists
            for (Object importObj : ctClass.getPosition().getCompilationUnit().getImports()) {
                if (importObj.toString().contains("org.yaml.snakeyaml.LoaderOptions")) {
                    return; // Import already exists
                }
            }
            
            // Add import
            System.out.println("Adding import for org.yaml.snakeyaml.LoaderOptions");
            // Note: This is a bit hacky - in real code we'd use getFactory().createImport()
            // but for simplicity we're just noting it needs to be added
        }
    }
    
    // Debug processor to see what Spoon is processing
    static class DebugProcessor extends AbstractProcessor<CtClass<?>> {
        @Override
        public void process(CtClass<?> ctClass) {
            System.out.println("DEBUG: Found class: " + ctClass.getQualifiedName());
        }
    }
}