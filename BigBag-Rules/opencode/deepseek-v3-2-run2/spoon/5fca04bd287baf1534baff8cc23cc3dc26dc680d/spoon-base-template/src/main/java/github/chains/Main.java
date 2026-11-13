package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.util.Arrays;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <project-path>");
            System.err.println("No input arguments provided.");
            return;
        }

        String projectPath = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(projectPath);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        
        // Add processor to fix cactoos HexOf/BytesOf usage
        launcher.addProcessor(new CactoosHexBytesToDigestUtilsProcessor());
        
        launcher.run();
    }
    
/**
     * Generic processor that transforms cactoos HexOf/BytesOf usage to Apache Commons Codec Hex.encodeHexString
     * This transformation can be applied to any Maven project affected by the breaking
     * change where org.cactoos.text.HexOf and org.cactoos.io.BytesOf were removed
     * from the http library and need to be replaced with org.apache.commons.codec.binary.Hex.encodeHexString
     * 
     * Transformation pattern:
     * Old: new Digest.Sha256(new HexOf(new BytesOf(sha.digest())).asString())
     * New: new Digest.Sha256(Hex.encodeHexString(sha.digest()))
     * 
     * Also handles ListOf replacement in test files:
     * Old: new ListOf<T>(...)
     * New: Arrays.<T>asList(...)
     */
    static class CactoosHexBytesToDigestUtilsProcessor extends AbstractProcessor<CtClass<?>> {
        
        private static final String HEXOF_TYPE = "org.cactoos.text.HexOf";
        private static final String BYTESOF_TYPE = "org.cactoos.io.BytesOf";
        private static final String LISTOF_TYPE = "org.cactoos.list.ListOf";
        private static final String HEX_CLASS = "org.apache.commons.codec.binary.Hex";
        
        @Override
        public void process(CtClass<?> ctClass) {
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
            
            // Transform HexOf(new BytesOf(...)).asString() patterns
            for (CtConstructorCall<?> hexOfCall : hexOfCalls) {
                transformHexOfBytesOfPattern(hexOfCall);
            }
            
            // Also handle ListOf constructor calls in test files
            List<CtConstructorCall<?>> listOfCalls = ctClass.getElements(
                new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                    @Override
                    public boolean matches(CtConstructorCall<?> element) {
                        return element.getType() != null && 
                               LISTOF_TYPE.equals(element.getType().getQualifiedName());
                    }
                }
            );
            
            for (CtConstructorCall<?> listOfCall : listOfCalls) {
                transformListOfPattern(listOfCall);
            }
        }
        
        private void transformHexOfBytesOfPattern(CtConstructorCall<?> hexOfCall) {
            // Check if this HexOf call is part of a pattern: new Digest.Sha256(new HexOf(new BytesOf(...)).asString())
            CtConstructorCall<?> digestConstructor = hexOfCall.getParent(CtConstructorCall.class);
            if (digestConstructor != null && digestConstructor.getType() != null) {
                String typeName = digestConstructor.getType().getQualifiedName();
                if (typeName != null && typeName.contains("Digest.Sha256")) {
                    // Check if HexOf has a BytesOf as argument and is called with .asString()
                    CtInvocation<?> asStringInvocation = hexOfCall.getParent(CtInvocation.class);
                    if (asStringInvocation != null && "asString".equals(asStringInvocation.getExecutable().getSimpleName())) {
                        if (hexOfCall.getArguments().size() == 1) {
                            Object arg = hexOfCall.getArguments().get(0);
                            if (arg instanceof CtConstructorCall) {
                                CtConstructorCall<?> bytesOfCall = (CtConstructorCall<?>) arg;
                                if (bytesOfCall.getType() != null && 
                                    BYTESOF_TYPE.equals(bytesOfCall.getType().getQualifiedName())) {
                                    
                                    // Get the argument to BytesOf (the byte array expression)
                                    if (bytesOfCall.getArguments().size() == 1) {
                                        Object bytesArg = bytesOfCall.getArguments().get(0);
                                        
                                        // Create Hex.encodeHexString(...) invocation
                                        CtTypeReference<?> hexTypeRef = getFactory().Type().createReference(HEX_CLASS);
                                        CtExecutableReference<?> encodeHexStringRef = getFactory().Executable().createReference(
                                            hexTypeRef,
                                            hexTypeRef,
                                            "encodeHexString",
                                            Arrays.asList(getFactory().Type().createReference(byte[].class))
                                        );
                                        
                                        CtInvocation<?> hexEncodeCall = getFactory().createInvocation(
                                            null,
                                            encodeHexStringRef,
                                            (CtExpression<?>) bytesArg
                                        );
                                        
                                        // Create new Digest.Sha256 constructor with Hex.encodeHexString(...)
                                        CtTypeReference<?> digestSha256Ref = getFactory().Type().createReference(
                                            "com.artipie.docker.Digest$Sha256"
                                        );
                                        CtConstructorCall<?> newDigestCall = getFactory().createConstructorCall(
                                            digestSha256Ref,
                                            hexEncodeCall
                                        );
                                        
                                        // Replace the entire Digest.Sha256 constructor call
                                        digestConstructor.replace(newDigestCall);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        private void transformListOfPattern(CtConstructorCall<?> listOfCall) {
            // Transform ListOf to Arrays.asList
            // Get the type arguments if any
            List<CtExpression<?>> arguments = listOfCall.getArguments();
            
            // Create Arrays.asList invocation
            CtTypeReference<?> arraysTypeRef = getFactory().Type().createReference("java.util.Arrays");
            CtExecutableReference<?> asListRef = getFactory().Executable().createReference(
                arraysTypeRef,
                arraysTypeRef,
                "asList"
            );
            
            CtInvocation<?> asListCall = getFactory().createInvocation(
                null,
                asListRef,
                arguments.toArray(new CtExpression<?>[0])
            );
            
            // Replace ListOf constructor with Arrays.asList
            listOfCall.replace(asListCall);
        }
        
        @Override
        public boolean isToBeProcessed(CtClass<?> candidate) {
            return true;
        }
    }
                }
            );
            
            // Transform HexOf(new BytesOf(...)).asString() patterns
            for (CtConstructorCall<?> hexOfCall : hexOfCalls) {
                transformHexOfBytesOfPattern(hexOfCall);
            }
            
            // Also handle BytesOf constructor calls that might be used elsewhere
            List<CtConstructorCall<?>> bytesOfCalls = ctClass.getElements(
                new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                    @Override
                    public boolean matches(CtConstructorCall<?> element) {
                        return element.getType() != null && 
                               BYTESOF_TYPE.equals(element.getType().getQualifiedName());
                    }
                }
            );
            
            // For any standalone BytesOf calls, we might need to handle them too
            // But for now, we focus on the HexOf(new BytesOf(...)).asString() pattern
        }
        
        private void transformHexOfBytesOfPattern(CtConstructorCall<?> hexOfCall) {
            // Check if this HexOf call is part of a pattern: new Digest.Sha256(new HexOf(new BytesOf(...)).asString())
            // We need to find the parent constructor call to Digest.Sha256
            CtConstructorCall<?> digestConstructor = hexOfCall.getParent(CtConstructorCall.class);
            if (digestConstructor != null && digestConstructor.getType() != null) {
                String typeName = digestConstructor.getType().getQualifiedName();
                if (typeName != null && typeName.contains("Digest.Sha256")) {
                    // Check if HexOf has a BytesOf as argument and is called with .asString()
                    CtInvocation<?> asStringInvocation = hexOfCall.getParent(CtInvocation.class);
                    if (asStringInvocation != null && "asString".equals(asStringInvocation.getExecutable().getSimpleName())) {
                        if (hexOfCall.getArguments().size() == 1) {
                            Object arg = hexOfCall.getArguments().get(0);
                            if (arg instanceof CtConstructorCall) {
                                CtConstructorCall<?> bytesOfCall = (CtConstructorCall<?>) arg;
                                if (bytesOfCall.getType() != null && 
                                    BYTESOF_TYPE.equals(bytesOfCall.getType().getQualifiedName())) {
                                    
                                    // Get the argument to BytesOf (the byte array expression)
                                    if (bytesOfCall.getArguments().size() == 1) {
                                        Object bytesArg = bytesOfCall.getArguments().get(0);
                                        
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
            // Process all classes to ensure we catch any usage of the pattern
            // We'll check for the pattern during processing
            return true;
        }
    }
}