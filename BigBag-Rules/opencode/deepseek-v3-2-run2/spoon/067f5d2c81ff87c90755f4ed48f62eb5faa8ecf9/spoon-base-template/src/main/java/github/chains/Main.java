package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtCatch;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtTry;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;

public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Add transformation
        launcher.addProcessor(new TSerializerConstructorTransformation());
        
        // Apply transformation with output
        java.io.File tempDir = new java.io.File("temp-output");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        
        // Create config directory to avoid Spoon errors
        java.io.File configDir = new java.io.File(tempDir, "config");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        
        launcher.getEnvironment().setSourceOutputDirectory(tempDir);
        launcher.getEnvironment().setShouldCompile(false);
        launcher.getEnvironment().setNoClasspath(true);
        
        launcher.run();
    }
    
    /**
     * Generic Spoon transformation to handle TSerializer constructor breaking change
     * in libthrift 0.16.0 where constructors now throw TTransportException
     */
    public static class TSerializerConstructorTransformation extends AbstractProcessor<CtConstructorCall<?>> {
        
        private static final String TSERIALIZER_FQN = "org.apache.thrift.TSerializer";
        
        @Override
        public void process(CtConstructorCall<?> constructorCall) {
            // Check if this is a TSerializer constructor call
            if (isTSerializerConstructor(constructorCall)) {
                // Check if the constructor call is already in a try-catch block
                if (!isInTryCatchBlock(constructorCall)) {
                    wrapInTryCatch(constructorCall);
                }
            }
        }
        
        private boolean isTSerializerConstructor(CtConstructorCall<?> constructorCall) {
            // Try to get the type from the constructor call
            String typeName = constructorCall.getType().toString();
            
            // Check if it matches TSerializer (with or without package)
            return typeName.contains("TSerializer");
        }
        
        private boolean isInTryCatchBlock(CtElement element) {
            CtElement parent = element.getParent();
            while (parent != null) {
                if (parent instanceof CtTry) {
                    return true;
                }
                parent = parent.getParent();
            }
            return false;
        }
        
        private void wrapInTryCatch(CtConstructorCall<?> constructorCall) {
            Factory factory = constructorCall.getFactory();
            
            // Create the entire try-catch block as a code snippet
            String tryCatchCode = "try { " +
                constructorCall + " } " +
                "catch (org.apache.thrift.transport.TTransportException e) { " +
                "System.err.println(\"Failed to create TSerializer: \" + e.getMessage()); " +
                "throw new RuntimeException(\"Failed to initialize TSerializer\", e); " +
                "}";
            
            // Check the parent context and replace appropriately
            CtElement parent = constructorCall.getParent();
            if (parent instanceof CtStatement) {
                // Replace statement with try-catch statement
                ((CtStatement) parent).replace(factory.createCodeSnippetStatement(tryCatchCode));
            } else if (parent instanceof spoon.reflect.declaration.CtField) {
                // For field declarations, we need to handle initialization differently
                // This is a complex case - for now, we'll skip field initializations
                System.err.println("Warning: TSerializer constructor in field initialization - skipping transformation");
            }
        }
    }
}