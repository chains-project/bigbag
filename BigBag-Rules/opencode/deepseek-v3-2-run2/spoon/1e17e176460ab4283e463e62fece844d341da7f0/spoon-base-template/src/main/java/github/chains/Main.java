package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Filter;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.visitor.ProcessingVisitor;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transform.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying SnakeYAML 2.0 migration to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setCopyResources(false);
        
        try {
            CtModel model = launcher.buildModel();
            
            // Transformation 1: Fix Yaml constructor calls
            transformYamlConstructorCalls(model, launcher);
            
            // Transformation 2: Fix Constructor constructor calls
            transformConstructorConstructorCalls(model, launcher);
            
            // Transformation 3: Fix Representer constructor calls
            transformRepresenterConstructorCalls(model, launcher);
            
            // Transformation 4: Fix getProperties method signatures
            transformGetPropertiesMethods(model, launcher);
            
            // Apply the transformations
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            
            System.out.println("Migration completed successfully!");
            
        } catch (Exception e) {
            System.err.println("Error during migration: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformYamlConstructorCalls(CtModel model, Launcher launcher) {
        // Find all constructor calls to org.yaml.snakeyaml.Yaml with 4 parameters
        List<CtConstructorCall<?>> yamlConstructorCalls = model
            .getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall<?> constructorCall) {
                    if (constructorCall.getType() == null) return false;
                    String typeName = constructorCall.getType().getQualifiedName();
                    return "org.yaml.snakeyaml.Yaml".equals(typeName) && 
                           constructorCall.getArguments().size() == 4;
                }
            });
        
        System.out.println("Found " + yamlConstructorCalls.size() + " Yaml constructor calls to migrate");
        
        for (CtConstructorCall<?> constructorCall : yamlConstructorCalls) {
            System.out.println("Migrating Yaml constructor at: " + constructorCall.getPosition());
            
            // Create factory from the original launcher
            var factory = launcher.getFactory();
            
            // Create new LoaderOptions() constructor call
            CtConstructorCall<?> loaderOptionsCall = factory.Code()
                .createConstructorCall(factory.Type().createReference("org.yaml.snakeyaml.LoaderOptions"));
            
            // Get the arguments
            var arguments = constructorCall.getArguments();
            
            // Rebuild the constructor call with LoaderOptions inserted as 4th argument
            // New signature: (arg0, arg1, arg2, new LoaderOptions(), arg3)
            CtConstructorCall<?> newConstructorCall = factory.Code()
                .createConstructorCall(factory.Type().createReference("org.yaml.snakeyaml.Yaml"));
            
            // Add first 3 arguments
            newConstructorCall.addArgument(arguments.get(0));
            newConstructorCall.addArgument(arguments.get(1));
            newConstructorCall.addArgument(arguments.get(2));
            // Add new LoaderOptions()
            newConstructorCall.addArgument(loaderOptionsCall);
            // Add original 4th argument (Resolver) as now 5th argument
            newConstructorCall.addArgument(arguments.get(3));
            
            // Replace the old constructor call with the new one
            constructorCall.replace(newConstructorCall);
            
            System.out.println("  -> Successfully migrated to 5-argument constructor with LoaderOptions");
        }
    }
    
    private static void transformConstructorConstructorCalls(CtModel model, Launcher launcher) {
        // Find all constructor calls to org.yaml.snakeyaml.constructor.Constructor
        List<CtConstructorCall<?>> constructorCalls = model
            .getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall<?> constructorCall) {
                    if (constructorCall.getType() == null) return false;
                    String typeName = constructorCall.getType().getQualifiedName();
                    return "org.yaml.snakeyaml.constructor.Constructor".equals(typeName);
                }
            });
        
        System.out.println("\nFound " + constructorCalls.size() + " Constructor constructor calls to migrate");
        
        for (CtConstructorCall<?> constructorCall : constructorCalls) {
            System.out.println("Migrating Constructor constructor at: " + constructorCall.getPosition());
            
            var factory = launcher.getFactory();
            var arguments = constructorCall.getArguments();
            
            // Check if it already has LoaderOptions
            boolean hasLoaderOptions = arguments.size() > 0 && 
                arguments.get(arguments.size() - 1).getType() != null &&
                "org.yaml.snakeyaml.LoaderOptions".equals(arguments.get(arguments.size() - 1).getType().getQualifiedName());
            
            if (!hasLoaderOptions) {
                // Add LoaderOptions as last argument
                CtConstructorCall<?> loaderOptionsCall = factory.Code()
                    .createConstructorCall(factory.Type().createReference("org.yaml.snakeyaml.LoaderOptions"));
                
                CtConstructorCall<?> newConstructorCall = factory.Code()
                    .createConstructorCall(factory.Type().createReference("org.yaml.snakeyaml.constructor.Constructor"));
                
                // Copy all existing arguments
                for (var arg : arguments) {
                    newConstructorCall.addArgument(arg);
                }
                // Add LoaderOptions
                newConstructorCall.addArgument(loaderOptionsCall);
                
                constructorCall.replace(newConstructorCall);
                System.out.println("  -> Added LoaderOptions parameter");
            } else {
                System.out.println("  -> Already has LoaderOptions, skipping");
            }
        }
    }
    
    private static void transformRepresenterConstructorCalls(CtModel model, Launcher launcher) {
        // Find all constructor calls to org.yaml.snakeyaml.representer.Representer
        List<CtConstructorCall<?>> representerCalls = model
            .getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall<?> constructorCall) {
                    if (constructorCall.getType() == null) return false;
                    String typeName = constructorCall.getType().getQualifiedName();
                    return "org.yaml.snakeyaml.representer.Representer".equals(typeName);
                }
            });
        
        System.out.println("\nFound " + representerCalls.size() + " Representer constructor calls to migrate");
        
        for (CtConstructorCall<?> constructorCall : representerCalls) {
            System.out.println("Migrating Representer constructor at: " + constructorCall.getPosition());
            
            var factory = launcher.getFactory();
            var arguments = constructorCall.getArguments();
            
            // Check if it already has DumperOptions
            boolean hasDumperOptions = arguments.size() > 0;
            
            if (!hasDumperOptions) {
                // Add DumperOptions as argument
                CtConstructorCall<?> dumperOptionsCall = factory.Code()
                    .createConstructorCall(factory.Type().createReference("org.yaml.snakeyaml.DumperOptions"));
                
                CtConstructorCall<?> newConstructorCall = factory.Code()
                    .createConstructorCall(factory.Type().createReference("org.yaml.snakeyaml.representer.Representer"));
                
                // Add DumperOptions
                newConstructorCall.addArgument(dumperOptionsCall);
                
                constructorCall.replace(newConstructorCall);
                System.out.println("  -> Added DumperOptions parameter");
            } else {
                System.out.println("  -> Already has DumperOptions, skipping");
            }
        }
    }
    
    private static void transformGetPropertiesMethods(CtModel model, Launcher launcher) {
        // Find all classes that extend Representer
        List<CtClass<?>> representerSubclasses = model
            .getElements(new TypeFilter<CtClass<?>>(CtClass.class) {
                @Override
                public boolean matches(CtClass<?> ctClass) {
                    CtTypeReference<?> superclass = ctClass.getSuperclass();
                    return superclass != null && 
                           "org.yaml.snakeyaml.representer.Representer".equals(superclass.getQualifiedName());
                }
            });
        
        System.out.println("\nFound " + representerSubclasses.size() + " Representer subclasses to check");
        
        for (CtClass<?> ctClass : representerSubclasses) {
            // Find getProperties methods
            for (CtMethod<?> method : ctClass.getMethods()) {
                if ("getProperties".equals(method.getSimpleName()) && 
                    method.getParameters().size() == 1 &&
                    method.getParameters().get(0).getType().getQualifiedName().equals("java.lang.Class")) {
                    
                    System.out.println("Found getProperties method in " + ctClass.getQualifiedName() + 
                                      " at: " + method.getPosition());
                    
                    // Check if it throws IntrospectionException
                    boolean throwsIntrospectionException = method.getThrownTypes().stream()
                        .anyMatch(t -> "java.beans.IntrospectionException".equals(t.getQualifiedName()));
                    
                    if (throwsIntrospectionException) {
                        System.out.println("  -> Removing IntrospectionException from throws clause");
                        
                        // Remove IntrospectionException from throws clause
                        method.setThrownTypes(method.getThrownTypes().stream()
                            .filter(t -> !"java.beans.IntrospectionException".equals(t.getQualifiedName()))
                            .collect(java.util.stream.Collectors.toSet()));
                    }
                }
            }
        }
    }
}