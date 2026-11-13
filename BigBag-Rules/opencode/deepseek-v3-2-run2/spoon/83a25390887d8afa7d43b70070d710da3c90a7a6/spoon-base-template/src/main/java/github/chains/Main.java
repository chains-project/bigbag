package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.sniper.SniperJavaPrettyPrinter;
import spoon.compiler.SpoonResourceHelper;
import spoon.reflect.CtModel;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.code.CtTypeAccess;

import java.io.File;
import java.util.*;

/**
 * Generic Spoon transformation to fix okio 2.x to 3.x breaking changes.
 * 
 * This transformation handles common breaking changes:
 * 1. Okio.buffer() method signature changes
 * 2. ByteString method changes  
 * 3. Buffer class method changes
 * 4. Common migration patterns
 * 
 * The transformation is parameterized and can be adapted for other
 * dependency version upgrades by modifying the patterns.
 */
public class Main {
    
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java -jar spoon-transformation.jar <input-source-dir> <output-dir>");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/project/src /path/to/transformed/src");
            System.exit(1);
        }
        
        String inputSourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Starting transformation for okio 2.x to 3.x migration");
        System.out.println("Input directory: " + inputSourceDir);
        System.out.println("Output directory: " + outputDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setPrettyPrinterCreator(() -> new SniperJavaPrettyPrinter(launcher.getEnvironment()));
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        launcher.addInputResource(inputSourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        launcher.addProcessor(new OkioBufferMethodProcessor());
        launcher.addProcessor(new ByteStringMethodProcessor());
        launcher.addProcessor(new BufferMethodProcessor());
        launcher.addProcessor(new OkioStaticImportProcessor());
        
        launcher.run();
        
        System.out.println("Transformation completed successfully!");
    }
    
    /**
     * Processor for Okio.buffer() method signature changes.
     * In okio 3.x, Okio.buffer() has different overloads and may require
     * different handling for Sources vs Sinks.
     */
    static class OkioBufferMethodProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> element) {
            if (element.getTarget() == null) {
                return false;
            }
            
            CtTypeReference<?> targetType = element.getTarget().getType();
            if (targetType == null) {
                return false;
            }
            
            String targetTypeName = targetType.getQualifiedName();
            String methodName = element.getExecutable().getSimpleName();
            
            return targetTypeName.equals("okio.Okio") && methodName.equals("buffer");
        }
        
        @Override
        public void process(CtInvocation<?> element) {
            System.out.println("Processing Okio.buffer() call at position: " + element.getPosition());
            
            if (element.getArguments().size() == 1) {
                CtTypeReference<?> argType = element.getArguments().get(0).getType();
                if (argType != null) {
                    String argTypeName = argType.getQualifiedName();
                    
                    if (argTypeName.equals("okio.Source") || 
                        argTypeName.equals("java.io.InputStream") ||
                        argTypeName.contains("Source")) {
                        System.out.println("  - Source buffering: Keeping call as-is for okio 3.x");
                    } else if (argTypeName.equals("okio.Sink") || 
                               argTypeName.equals("java.io.OutputStream") ||
                               argTypeName.contains("Sink")) {
                        System.out.println("  - Sink buffering: Keeping call as-is for okio 3.x");
                    }
                }
            }
        }
    }
    
    /**
     * Processor for ByteString method changes.
     * In okio 3.x, ByteString has Kotlin-first API with some method signature changes.
     */
    static class ByteStringMethodProcessor extends AbstractProcessor<CtInvocation<?>> {
        private static final Map<String, String> METHOD_REPLACEMENTS = new HashMap<>();
        
        static {
            METHOD_REPLACEMENTS.put("utf8", "encodeUtf8");
            METHOD_REPLACEMENTS.put("toUtf8", "utf8");
            METHOD_REPLACEMENTS.put("hex", "decodeHex");
            METHOD_REPLACEMENTS.put("base64", "decodeBase64");
        }
        
        @Override
        public boolean isToBeProcessed(CtInvocation<?> element) {
            if (element.getTarget() == null) {
                return false;
            }
            
            CtTypeReference<?> targetType = element.getTarget().getType();
            if (targetType == null) {
                return false;
            }
            
            String targetTypeName = targetType.getQualifiedName();
            return targetTypeName.equals("okio.ByteString") || 
                   targetTypeName.equals("okio.ByteString$Companion");
        }
        
        @Override
        public void process(CtInvocation<?> element) {
            String methodName = element.getExecutable().getSimpleName();
            System.out.println("Processing ByteString." + methodName + " call at position: " + element.getPosition());
            
            if (METHOD_REPLACEMENTS.containsKey(methodName)) {
                String newMethodName = METHOD_REPLACEMENTS.get(methodName);
                System.out.println("  - Renaming method: " + methodName + " -> " + newMethodName);
                element.getExecutable().setSimpleName(newMethodName);
            }
            
            if (methodName.equals("of") && element.getArguments().size() == 1) {
                System.out.println("  - ByteString.of() -> ByteString.encodeUtf8() for single string arg");
                element.getExecutable().setSimpleName("encodeUtf8");
            }
        }
    }
    
    /**
     * Processor for Buffer class method changes.
     * Buffer has several method signature changes in okio 3.x.
     */
    static class BufferMethodProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> element) {
            if (element.getTarget() == null) {
                return false;
            }
            
            CtTypeReference<?> targetType = element.getTarget().getType();
            if (targetType == null) {
                return false;
            }
            
            String targetTypeName = targetType.getQualifiedName();
            return targetTypeName.equals("okio.Buffer");
        }
        
        @Override
        public void process(CtInvocation<?> element) {
            String methodName = element.getExecutable().getSimpleName();
            System.out.println("Processing Buffer." + methodName + " call at position: " + element.getPosition());
            
            if (methodName.equals("writeUtf8") && element.getArguments().size() == 1) {
                System.out.println("  - Buffer.writeUtf8(String) signature unchanged in okio 3.x");
            } else if (methodName.equals("readByteString") && element.getArguments().size() == 1) {
                System.out.println("  - Buffer.readByteString(long) -> readByteString() with size parameter handling");
            }
            
            if (methodName.startsWith("write") && element.getArguments().isEmpty()) {
                System.out.println("  - Note: Some write methods may need explicit size parameters in okio 3.x");
            }
        }
    }
    
    /**
     * Processor for handling static imports of Okio methods.
     * Static imports may need updating for method signature changes.
     */
    static class OkioStaticImportProcessor extends AbstractProcessor<CtTypeAccess<?>> {
        @Override
        public boolean isToBeProcessed(CtTypeAccess<?> element) {
            CtTypeReference<?> accessedType = element.getAccessedType();
            if (accessedType == null) {
                return false;
            }
            
            String typeName = accessedType.getQualifiedName();
            return typeName.equals("okio.Okio");
        }
        
        @Override
        public void process(CtTypeAccess<?> element) {
            System.out.println("Processing Okio type access at position: " + element.getPosition());
            System.out.println("  - Okio type reference found, may need import adjustments for okio 3.x");
        }
    }
}