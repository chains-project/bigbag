package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.visitor.ClassTypingContext;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying snakeyaml 2.0 migration transformations to: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        int transformCount = 0;
        
        // Pattern 1: Fix Constructor(Class) calls - add LoaderOptions parameter
        List<CtConstructorCall<?>> constructorCalls = model.getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall<?> element) {
                CtTypeReference<?> typeRef = element.getType();
                if (typeRef == null) return false;
                
                String typeName = typeRef.getQualifiedName();
                return typeName != null && 
                       (typeName.equals("org.yaml.snakeyaml.constructor.Constructor") ||
                        typeName.equals("org.yaml.snakeyaml.constructor.SafeConstructor"));
            }
        });
        
        for (CtConstructorCall<?> constructorCall : constructorCalls) {
            List<?> arguments = constructorCall.getArguments();
            
            if (arguments.size() == 0) {
                // Case: Constructor() -> Constructor(new LoaderOptions())
                System.out.println("Transforming Constructor() -> Constructor(new LoaderOptions()) at: " + 
                                  constructorCall.getPosition());
                try {
                    CtConstructorCall<?> loaderOptionsCall = launcher.getFactory().Code().createConstructorCall(
                        launcher.getFactory().Type().createReference("org.yaml.snakeyaml.LoaderOptions")
                    );
                    constructorCall.addArgument(loaderOptionsCall);
                    transformCount++;
                } catch (Exception e) {
                    System.err.println("Error: " + e.getMessage());
                }
            } else if (arguments.size() == 1) {
                // Case: Constructor(Class) -> Constructor(Class, new LoaderOptions())
                // Check if argument looks like a Class reference (e.g., Model.class)
                Object firstArg = arguments.get(0);
                boolean isClassArg = false;
                
                if (firstArg instanceof CtLiteral) {
                    // Could be a class literal
                    isClassArg = true;
                } else if (firstArg instanceof CtInvocation) {
                    CtInvocation<?> invocation = (CtInvocation<?>) firstArg;
                    if (invocation.getExecutable() != null && 
                        "class".equals(invocation.getExecutable().getSimpleName())) {
                        isClassArg = true;
                    }
                } else {
                    // Could be a variable or expression representing a Class
                    // We'll assume it's a Class argument for transformation
                    isClassArg = true;
                }
                
                if (isClassArg) {
                    System.out.println("Transforming Constructor(Class) -> Constructor(Class, new LoaderOptions()) at: " + 
                                      constructorCall.getPosition());
                    try {
                        CtConstructorCall<?> loaderOptionsCall = launcher.getFactory().Code().createConstructorCall(
                            launcher.getFactory().Type().createReference("org.yaml.snakeyaml.LoaderOptions")
                        );
                        constructorCall.addArgument(loaderOptionsCall);
                        transformCount++;
                    } catch (Exception e) {
                        System.err.println("Error: " + e.getMessage());
                    }
                }
            }
        }
        
        // Pattern 2: Fix Representer() calls - add DumperOptions parameter
        List<CtConstructorCall<?>> representerCalls = model.getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall<?> element) {
                CtTypeReference<?> typeRef = element.getType();
                if (typeRef == null) return false;
                
                String typeName = typeRef.getQualifiedName();
                return typeName != null && typeName.equals("org.yaml.snakeyaml.representer.Representer");
            }
        });
        
        for (CtConstructorCall<?> representerCall : representerCalls) {
            List<?> arguments = representerCall.getArguments();
            
            if (arguments.isEmpty()) {
                // Case: Representer() -> Representer(new DumperOptions())
                System.out.println("Transforming Representer() -> Representer(new DumperOptions()) at: " + 
                                  representerCall.getPosition());
                try {
                    CtConstructorCall<?> dumperOptionsCall = launcher.getFactory().Code().createConstructorCall(
                        launcher.getFactory().Type().createReference("org.yaml.snakeyaml.DumperOptions")
                    );
                    representerCall.addArgument(dumperOptionsCall);
                    transformCount++;
                } catch (Exception e) {
                    System.err.println("Error: " + e.getMessage());
                }
            }
        }
        
        // Pattern 3: Remove IntrospectionException from getProperties methods in Representer subclasses
        List<CtMethod<?>> methods = model.getElements(new TypeFilter<CtMethod<?>>(CtMethod.class) {
            @Override
            public boolean matches(CtMethod<?> element) {
                if (!"getProperties".equals(element.getSimpleName())) {
                    return false;
                }
                
                // Check if method has one parameter of type Class
                if (element.getParameters().size() != 1) {
                    return false;
                }
                
                CtTypeReference<?> paramType = element.getParameters().get(0).getType();
                if (paramType == null || !"java.lang.Class".equals(paramType.getQualifiedName())) {
                    return false;
                }
                
                return true;
            }
        });
        
        for (CtMethod<?> method : methods) {
            // Check if method is in a class that extends Representer
            CtType<?> declaringType = method.getDeclaringType();
            if (declaringType instanceof CtClass) {
                CtClass<?> clazz = (CtClass<?>) declaringType;
                CtTypeReference<?> superClass = clazz.getSuperclass();
                
                // Check if class extends Representer (directly or indirectly)
                boolean extendsRepresenter = false;
                while (superClass != null) {
                    if ("org.yaml.snakeyaml.representer.Representer".equals(superClass.getQualifiedName())) {
                        extendsRepresenter = true;
                        break;
                    }
                    // Get superclass of superclass
                    if (superClass.getTypeDeclaration() instanceof CtClass) {
                        superClass = ((CtClass<?>) superClass.getTypeDeclaration()).getSuperclass();
                    } else {
                        break;
                    }
                }
                
                if (extendsRepresenter) {
                    // Check if method throws IntrospectionException
                    boolean hasIntrospectionException = method.getThrownTypes().stream()
                        .anyMatch(t -> "java.beans.IntrospectionException".equals(t.getQualifiedName()));
                    
                    if (hasIntrospectionException) {
                        System.out.println("Removing IntrospectionException from getProperties in " + 
                                          declaringType.getQualifiedName() + " at: " + method.getPosition());
                        
                        method.getThrownTypes().removeIf(
                            t -> "java.beans.IntrospectionException".equals(t.getQualifiedName())
                        );
                        transformCount++;
                    }
                }
            }
        }
        
        // Pattern 4: Handle subclasses of Constructor that might have different constructor signatures
        // We need to find classes extending Constructor and fix their constructors
        List<CtClass<?>> constructorSubclasses = model.getElements(new TypeFilter<CtClass<?>>(CtClass.class) {
            @Override
            public boolean matches(CtClass<?> element) {
                CtTypeReference<?> superClass = element.getSuperclass();
                while (superClass != null) {
                    if ("org.yaml.snakeyaml.constructor.Constructor".equals(superClass.getQualifiedName()) ||
                        "org.yaml.snakeyaml.constructor.SafeConstructor".equals(superClass.getQualifiedName())) {
                        return true;
                    }
                    // Check further up the hierarchy
                    if (superClass.getTypeDeclaration() instanceof CtClass) {
                        superClass = ((CtClass<?>) superClass.getTypeDeclaration()).getSuperclass();
                    } else {
                        break;
                    }
                }
                return false;
            }
        });
        
        for (CtClass<?> subclass : constructorSubclasses) {
            for (CtConstructor<?> constructor : subclass.getConstructors()) {
                // Check if constructor calls super() with 0 or 1 argument
                if (constructor.getBody() != null) {
                    List<CtConstructorCall<?>> superCalls = constructor.getBody().getElements(
                        new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {}
                    );
                    
                    for (CtConstructorCall<?> superCall : superCalls) {
                        // Check if this is a super() call to Constructor
                        CtTypeReference<?> superCallType = superCall.getType();
                        if (superCallType != null && 
                            ("org.yaml.snakeyaml.constructor.Constructor".equals(superCallType.getQualifiedName()) ||
                             "org.yaml.snakeyaml.constructor.SafeConstructor".equals(superCallType.getQualifiedName()))) {
                            
                            List<?> superArgs = superCall.getArguments();
                            if (superArgs.size() == 0 || superArgs.size() == 1) {
                                // Similar transformation as Pattern 1
                                System.out.println("Transforming super() call in " + subclass.getQualifiedName() + 
                                                  " constructor at: " + superCall.getPosition());
                                
                                try {
                                    CtConstructorCall<?> loaderOptionsCall = launcher.getFactory().Code().createConstructorCall(
                                        launcher.getFactory().Type().createReference("org.yaml.snakeyaml.LoaderOptions")
                                    );
                                    
                                    if (superArgs.size() == 0) {
                                        // super() -> super(new LoaderOptions())
                                        superCall.addArgument(loaderOptionsCall);
                                    } else {
                                        // super(Class) -> super(Class, new LoaderOptions())
                                        superCall.addArgument(loaderOptionsCall);
                                    }
                                    transformCount++;
                                } catch (Exception e) {
                                    System.err.println("Error transforming super() call: " + e.getMessage());
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Pattern 5: Handle subclasses of Representer that don't explicitly call super()
        List<CtClass<?>> representerSubclasses = model.getElements(new TypeFilter<CtClass<?>>(CtClass.class) {
            @Override
            public boolean matches(CtClass<?> element) {
                CtTypeReference<?> superClass = element.getSuperclass();
                while (superClass != null) {
                    if ("org.yaml.snakeyaml.representer.Representer".equals(superClass.getQualifiedName())) {
                        return true;
                    }
                    // Check further up the hierarchy
                    if (superClass.getTypeDeclaration() instanceof CtClass) {
                        superClass = ((CtClass<?>) superClass.getTypeDeclaration()).getSuperclass();
                    } else {
                        break;
                    }
                }
                return false;
            }
        });
        
        for (CtClass<?> subclass : representerSubclasses) {
            for (CtConstructor<?> constructor : subclass.getConstructors()) {
                // Check if constructor doesn't have an explicit super() call
                if (constructor.getBody() != null) {
                    boolean hasExplicitSuperCall = false;
                    List<CtConstructorCall<?>> superCalls = constructor.getBody().getElements(
                        new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {}
                    );
                    
                    for (CtConstructorCall<?> superCall : superCalls) {
                        CtTypeReference<?> superCallType = superCall.getType();
                        if (superCallType != null && 
                            "org.yaml.snakeyaml.representer.Representer".equals(superCallType.getQualifiedName())) {
                            hasExplicitSuperCall = true;
                            break;
                        }
                    }
                    
                    if (!hasExplicitSuperCall) {
                        // Need to add super(new DumperOptions()) as first statement
                        System.out.println("Adding super(new DumperOptions()) to " + subclass.getQualifiedName() + 
                                          " constructor at: " + constructor.getPosition());
                        
                        try {
                            CtConstructorCall<?> dumperOptionsCall = launcher.getFactory().Code().createConstructorCall(
                                launcher.getFactory().Type().createReference("org.yaml.snakeyaml.DumperOptions")
                            );
                            CtConstructorCall<?> superCall = launcher.getFactory().Code().createConstructorCall(
                                launcher.getFactory().Type().createReference("org.yaml.snakeyaml.representer.Representer"),
                                dumperOptionsCall
                            );
                            
                            // Insert as first statement
                            constructor.getBody().insertBegin(superCall);
                            transformCount++;
                        } catch (Exception e) {
                            System.err.println("Error adding super() call: " + e.getMessage());
                        }
                    }
                }
            }
        }
        
        // Output summary
        System.out.println("\nApplied " + transformCount + " transformation(s)");
        
        // Write transformed code
        launcher.setSourceOutputDirectory(sourceDir + "-transformed");
        launcher.prettyprint();
        
        System.out.println("Transformed code written to: " + sourceDir + "-transformed");
    }
}