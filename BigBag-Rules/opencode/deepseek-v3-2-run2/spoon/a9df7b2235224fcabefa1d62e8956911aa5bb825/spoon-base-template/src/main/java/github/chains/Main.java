package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.visitor.SignaturePrinter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying snakeyaml 1.32 migration transformation to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        var factory = launcher.getFactory();
        
        int transformations = 0;
        
        // Transformation 1: Fix Yaml constructor calls that need LoaderOptions
        List<CtConstructorCall<?>> yamlConstructorCalls = Query.getElements(factory, 
            new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall<?> constructorCall) {
                    try {
                        String typeName = constructorCall.getType().getQualifiedName();
                        if ("org.yaml.snakeyaml.Yaml".equals(typeName)) {
                            int argCount = constructorCall.getArguments().size();
                            
                            // Check if it's the 4-parameter constructor: Yaml(BaseConstructor, Representer, DumperOptions, Resolver)
                            if (argCount == 4) {
                                // Check argument types if possible
                                System.out.println("Found Yaml constructor call with " + argCount + " parameters at: " + 
                                    constructorCall.getPosition().toString());
                                return true;
                            }
                        }
                    } catch (Exception e) {
                        // Skip if we can't determine type
                    }
                    return false;
                }
            });
        
        for (CtConstructorCall<?> constructorCall : yamlConstructorCalls) {
            System.out.println("Transforming Yaml constructor at: " + constructorCall.getPosition());
            
            // Add LoaderOptions as 4th parameter, shift Resolver to 5th
            // new Yaml(constructor, representer, dumperOptions, resolver)
            // becomes: new Yaml(constructor, representer, dumperOptions, new LoaderOptions(), resolver)
            try {
                // Get the resolver argument (currently 4th)
                var resolverArg = constructorCall.getArguments().get(3);
                
                // Create new LoaderOptions() call
                var loaderOptionsType = factory.Type().createReference("org.yaml.snakeyaml.LoaderOptions");
                var loaderOptionsConstructor = factory.Code().createConstructorCall(loaderOptionsType);
                
                // Set arguments: keep first 3, add LoaderOptions, then Resolver
                var constructorArgs = constructorCall.getArguments();
                constructorArgs.set(3, loaderOptionsConstructor); // Replace resolver with LoaderOptions
                constructorArgs.add(resolverArg); // Add resolver back as 5th argument
                
                constructorCall.setArguments(constructorArgs);
                transformations++;
                System.out.println("  -> Added LoaderOptions parameter");
            } catch (Exception e) {
                System.err.println("Error transforming Yaml constructor: " + e.getMessage());
            }
        }
        
        // Transformation 2: Fix Constructor class constructor calls
        List<CtConstructorCall<?>> constructorCalls = Query.getElements(factory,
            new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall<?> constructorCall) {
                    try {
                        String typeName = constructorCall.getType().getQualifiedName();
                        if ("org.yaml.snakeyaml.constructor.Constructor".equals(typeName) ||
                            "org.yaml.snakeyaml.constructor.SafeConstructor".equals(typeName)) {
                            int argCount = constructorCall.getArguments().size();
                            System.out.println("Found Constructor/SafeConstructor call with " + argCount + " parameters at: " +
                                constructorCall.getPosition().toString());
                            return true;
                        }
                    } catch (Exception e) {
                        // Skip if we can't determine type
                    }
                    return false;
                }
            });
        
        for (CtConstructorCall<?> constructorCall : constructorCalls) {
            System.out.println("Transforming Constructor call at: " + constructorCall.getPosition());
            
            try {
                int argCount = constructorCall.getArguments().size();
                
                if (argCount == 1) {
                    // Constructor(Class) -> Constructor(Class, LoaderOptions)
                    var loaderOptionsType = factory.Type().createReference("org.yaml.snakeyaml.LoaderOptions");
                    var loaderOptionsConstructor = factory.Code().createConstructorCall(loaderOptionsType);
                    
                    var constructorArgs = constructorCall.getArguments();
                    constructorArgs.add(loaderOptionsConstructor);
                    constructorCall.setArguments(constructorArgs);
                    transformations++;
                    System.out.println("  -> Added LoaderOptions parameter to Constructor(Class)");
                } else if (argCount == 0) {
                    // Constructor() -> Constructor(LoaderOptions)
                    var loaderOptionsType = factory.Type().createReference("org.yaml.snakeyaml.LoaderOptions");
                    var loaderOptionsConstructor = factory.Code().createConstructorCall(loaderOptionsType);
                    
                    constructorCall.setArguments(java.util.List.of(loaderOptionsConstructor));
                    transformations++;
                    System.out.println("  -> Changed Constructor() to Constructor(LoaderOptions)");
                }
            } catch (Exception e) {
                System.err.println("Error transforming Constructor call: " + e.getMessage());
            }
        }
        
        // Transformation 3: Fix BaseConstructor constructor calls
        List<CtConstructorCall<?>> baseConstructorCalls = Query.getElements(factory,
            new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall<?> constructorCall) {
                    try {
                        String typeName = constructorCall.getType().getQualifiedName();
                        if ("org.yaml.snakeyaml.constructor.BaseConstructor".equals(typeName)) {
                            int argCount = constructorCall.getArguments().size();
                            System.out.println("Found BaseConstructor call with " + argCount + " parameters at: " +
                                constructorCall.getPosition().toString());
                            return true;
                        }
                    } catch (Exception e) {
                        // Skip if we can't determine type
                    }
                    return false;
                }
            });
        
        for (CtConstructorCall<?> constructorCall : baseConstructorCalls) {
            System.out.println("Transforming BaseConstructor call at: " + constructorCall.getPosition());
            
            try {
                int argCount = constructorCall.getArguments().size();
                if (argCount == 0) {
                    // BaseConstructor() -> BaseConstructor(LoaderOptions)
                    var loaderOptionsType = factory.Type().createReference("org.yaml.snakeyaml.LoaderOptions");
                    var loaderOptionsConstructor = factory.Code().createConstructorCall(loaderOptionsType);
                    
                    constructorCall.setArguments(java.util.List.of(loaderOptionsConstructor));
                    transformations++;
                    System.out.println("  -> Changed BaseConstructor() to BaseConstructor(LoaderOptions)");
                }
            } catch (Exception e) {
                System.err.println("Error transforming BaseConstructor call: " + e.getMessage());
            }
        }
        
        // Apply all transformations
        if (transformations > 0) {
            System.out.println("\nApplied " + transformations + " transformations");
            
            // Output transformed code
            for (CtType<?> type : model.getAllTypes()) {
                if (type instanceof CtClass) {
                    System.out.println("\n--- Transformed class: " + type.getQualifiedName() + " ---");
                    System.out.println(type.toString());
                }
            }
            
            // Save transformed files
            launcher.setSourceOutputDirectory(sourceDir + "/transformed");
            launcher.prettyprint();
            System.out.println("\nTransformed code saved to: " + sourceDir + "/transformed");
        } else {
            System.out.println("No transformations applied");
        }
        
        System.out.println("\nTransformation complete!");
    }
}