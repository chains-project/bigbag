package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.CtConstructorCallImpl;
import org.apache.commons.codec.binary.Hex;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        
        // Add processor to fix HexOf and BytesOf usage
        launcher.addProcessor(new AbstractProcessor<CtConstructorCall<?>>() {
            @Override
            public void process(CtConstructorCall<?> constructorCall) {
                // Check if this is a HexOf constructor call
                if (isHexOfConstructor(constructorCall)) {
                    // Get the argument (should be a BytesOf constructor call)
                    List<CtExpression<?>> arguments = constructorCall.getArguments();
                    if (arguments.size() == 1 && isBytesOfConstructor(arguments.get(0))) {
                        CtConstructorCall<?> bytesOfCall = (CtConstructorCall<?>) arguments.get(0);
                        List<CtExpression<?>> bytesOfArgs = bytesOfCall.getArguments();
                        
                        if (bytesOfArgs.size() == 1) {
                            // Create Hex.encodeHexString() invocation
                            Factory factory = getFactory();
                            CtTypeReference<Hex> hexType = factory.createCtTypeReference(Hex.class);
                            CtTypeAccess<Hex> hexTypeAccess = factory.createTypeAccess(hexType);
                            
                            CtInvocation<String> hexInvocation = factory.createInvocation(
                                hexTypeAccess,
                                factory.createExecutableReference().setSimpleName("encodeHexString"),
                                bytesOfArgs.get(0)
                            );
                            
                            // Replace the entire HexOf(BytesOf(...)).asString() chain
                            // First check if this is part of a method call chain (.asString())
                            if (constructorCall.getParent() instanceof CtInvocation) {
                                CtInvocation<?> parentInvocation = (CtInvocation<?>) constructorCall.getParent();
                                if ("asString".equals(parentInvocation.getExecutable().getSimpleName())) {
                                    // Replace the entire invocation with our hex string
                                    parentInvocation.replace(hexInvocation);
                                    System.out.println("Fixed HexOf(BytesOf(...)).asString() at " + 
                                        constructorCall.getPosition());
                                    
                                    // Also need to add import for Hex class
                                    CtType<?> type = constructorCall.getParent(CtType.class);
                                    if (type != null) {
                                        type.addImport(hexType);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            private boolean isHexOfConstructor(CtConstructorCall<?> call) {
                try {
                    return call.getType() != null && 
                           "org.cactoos.text.HexOf".equals(call.getType().getQualifiedName());
                } catch (Exception e) {
                    return false;
                }
            }
            
            private boolean isBytesOfConstructor(CtExpression<?> expr) {
                if (expr instanceof CtConstructorCall) {
                    CtConstructorCall<?> call = (CtConstructorCall<?>) expr;
                    try {
                        return call.getType() != null && 
                               "org.cactoos.io.BytesOf".equals(call.getType().getQualifiedName());
                    } catch (Exception e) {
                        return false;
                    }
                }
                return false;
            }
        });
        
        // Add processor to remove old imports
        launcher.addProcessor(new AbstractProcessor<CtType<?>>() {
            @Override
            public void process(CtType<?> type) {
                // Remove imports for cactoos classes
                type.getImports().removeIf(imp -> {
                    String importStr = imp.toString();
                    return importStr.contains("org.cactoos.io.BytesOf") || 
                           importStr.contains("org.cactoos.text.HexOf");
                });
            }
        });
        
        try {
            launcher.run();
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        
        // Add processor to fix HexOf and BytesOf usage
        launcher.addProcessor(new AbstractProcessor<CtConstructorCall<?>>() {
            @Override
            public void process(CtConstructorCall<?> constructorCall) {
                // Check if this is a HexOf constructor call
                if (isHexOfConstructor(constructorCall)) {
                    // Get the argument (should be a BytesOf constructor call)
                    List<CtExpression<?>> arguments = constructorCall.getArguments();
                    if (arguments.size() == 1 && isBytesOfConstructor(arguments.get(0))) {
                        CtConstructorCall<?> bytesOfCall = (CtConstructorCall<?>) arguments.get(0);
                        List<CtExpression<?>> bytesOfArgs = bytesOfCall.getArguments();
                        
                        if (bytesOfArgs.size() == 1) {
                            // Create Hex.encodeHexString() invocation
                            Factory factory = getFactory();
                            CtTypeReference<Hex> hexType = factory.createCtTypeReference(Hex.class);
                            CtTypeAccess<Hex> hexTypeAccess = factory.createTypeAccess(hexType);
                            
                            CtInvocation<String> hexInvocation = factory.createInvocation(
                                hexTypeAccess,
                                factory.createExecutableReference().setSimpleName("encodeHexString"),
                                bytesOfArgs.get(0)
                            );
                            
                            // Replace the entire HexOf(BytesOf(...)).asString() chain
                            // First check if this is part of a method call chain (.asString())
                            if (constructorCall.getParent() instanceof CtInvocation) {
                                CtInvocation<?> parentInvocation = (CtInvocation<?>) constructorCall.getParent();
                                if ("asString".equals(parentInvocation.getExecutable().getSimpleName())) {
                                    // Replace the entire invocation with our hex string
                                    parentInvocation.replace(hexInvocation);
                                    System.out.println("Fixed HexOf(BytesOf(...)).asString() at " + 
                                        constructorCall.getPosition());
                                }
                            }
                        }
                    }
                }
            }
            
            private boolean isHexOfConstructor(CtConstructorCall<?> call) {
                try {
                    return call.getType() != null && 
                           "org.cactoos.text.HexOf".equals(call.getType().getQualifiedName());
                } catch (Exception e) {
                    return false;
                }
            }
            
            private boolean isBytesOfConstructor(CtExpression<?> expr) {
                if (expr instanceof CtConstructorCall) {
                    CtConstructorCall<?> call = (CtConstructorCall<?>) expr;
                    try {
                        return call.getType() != null && 
                               "org.cactoos.io.BytesOf".equals(call.getType().getQualifiedName());
                    } catch (Exception e) {
                        return false;
                    }
                }
                return false;
            }
        });
        
        try {
            launcher.run();
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}