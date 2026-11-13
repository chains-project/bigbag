package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

public class SimpleMain {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java SimpleMain <project-path>");
            return;
        }

        String projectPath = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(projectPath + "/src/main/java/com/artipie/docker/misc");
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setLevel("OFF"); // Reduce logging
        
        // Add processor to fix cactoos HexOf/BytesOf usage
        launcher.addProcessor(new CactoosHexBytesToDigestUtilsProcessor());
        
        try {
            launcher.run();
            System.out.println("Transformation completed successfully!");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    static class CactoosHexBytesToDigestUtilsProcessor extends AbstractProcessor<CtClass<?>> {
        
        private static final String HEXOF_TYPE = "org.cactoos.text.HexOf";
        private static final String BYTESOF_TYPE = "org.cactoos.io.BytesOf";
        
        @Override
        public void process(CtClass<?> ctClass) {
            System.out.println("Processing class: " + ctClass.getQualifiedName());
            
            // Find all constructor calls to HexOf
            List<CtConstructorCall<?>> hexOfCalls = ctClass.getElements(
                new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                    @Override
                    public boolean matches(CtConstructorCall<?> element) {
                        return element.getType() != null && 
                               HEXOF_TYPE.equals(element.getType().getQualifiedName());
                    }
                }
            );
            
            System.out.println("Found " + hexOfCalls.size() + " HexOf constructor calls");
            
            // Transform HexOf(new BytesOf(...)).asString() patterns
            for (CtConstructorCall<?> hexOfCall : hexOfCalls) {
                transformHexOfBytesOfPattern(hexOfCall);
            }
        }
        
        private void transformHexOfBytesOfPattern(CtConstructorCall<?> hexOfCall) {
            System.out.println("Transforming HexOf call at position: " + hexOfCall.getPosition());
            
            // Check if this HexOf call is part of a pattern: new Digest.Sha256(new HexOf(new BytesOf(...)).asString())
            // We need to find the parent constructor call to Digest.Sha256
            CtConstructorCall<?> digestConstructor = hexOfCall.getParent(CtConstructorCall.class);
            if (digestConstructor != null && digestConstructor.getType() != null) {
                String typeName = digestConstructor.getType().getQualifiedName();
                if (typeName != null && typeName.contains("Digest.Sha256")) {
                    System.out.println("Found Digest.Sha256 constructor containing HexOf");
                    
                    // Check if HexOf has a BytesOf as argument and is called with .asString()
                    CtInvocation<?> asStringInvocation = hexOfCall.getParent(CtInvocation.class);
                    if (asStringInvocation != null && "asString".equals(asStringInvocation.getExecutable().getSimpleName())) {
                        System.out.println("Found asString() invocation");
                        
                        if (hexOfCall.getArguments().size() == 1) {
                            Object arg = hexOfCall.getArguments().get(0);
                            if (arg instanceof CtConstructorCall) {
                                CtConstructorCall<?> bytesOfCall = (CtConstructorCall<?>) arg;
                                if (bytesOfCall.getType() != null && 
                                    BYTESOF_TYPE.equals(bytesOfCall.getType().getQualifiedName())) {
                                    
                                    System.out.println("Found BytesOf constructor inside HexOf");
                                    
                                    // Get the argument to BytesOf (the byte array expression)
                                    if (bytesOfCall.getArguments().size() == 1) {
                                        Object bytesArg = bytesOfCall.getArguments().get(0);
                                        System.out.println("BytesOf argument: " + bytesArg.getClass().getName());
                                        
                                        // Replace the entire Digest.Sha256 constructor call with new Digest.Sha256(bytesArg)
                                        // Create a new constructor call to Digest.Sha256 with the byte array directly
                                        CtTypeReference<?> digestSha256Ref = getFactory().Type().createReference(
                                            "com.artipie.docker.Digest$Sha256"
                                        );
                                        CtConstructorCall<?> newDigestCall = getFactory().createConstructorCall(
                                            digestSha256Ref,
                                            (CtExpression<?>) bytesArg
                                        );
                                        
                                        // Replace the entire Digest.Sha256 constructor call
                                        digestConstructor.replace(newDigestCall);
                                        System.out.println("Successfully replaced Digest.Sha256 constructor!");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        @Override
        public boolean isToBeProcessed(CtClass<?> candidate) {
            return true;
        }
    }
}