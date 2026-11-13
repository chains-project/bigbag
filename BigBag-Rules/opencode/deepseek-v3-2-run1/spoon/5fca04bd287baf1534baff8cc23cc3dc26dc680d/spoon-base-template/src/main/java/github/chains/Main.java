package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import java.util.List;

/**
 * Generic Spoon transformation to fix breaking changes from cactoos removal
 * in com.artipie:http v1.1.3.
 * 
 * Breaking Change Analysis:
 * - Old API pattern: new HexOf(new BytesOf(byteArray)).asString()
 * - New API pattern: bytesToHex(byteArray) (custom implementation)
 * - Transformation: Replace HexOf(BytesOf(byteArray)).asString() with bytesToHex(byteArray)
 * 
 * - Old API pattern: new ListOf<T>(elements...)
 * - New API pattern: Arrays.asList(elements...)
 * - Transformation: Replace ListOf with Arrays.asList
 * 
 * This transformation is generic and can be applied to any project affected
 * by the cactoos dependency removal in com.artipie:http v1.1.3.
 */
public class Main {
    
    /**
     * Replaces HexOf(BytesOf(byteArray)).asString() pattern.
     * This processor matches the exact pattern and replaces it with
     * a call to bytesToHex method.
     */
    public static class HexBytesOfReplacementProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        @Override
        public boolean isToBeProcessed(CtInvocation<?> invocation) {
            // Match .asString() calls
            if (!"asString".equals(invocation.getExecutable().getSimpleName())) {
                return false;
            }
            
            // Check if target is HexOf constructor
            CtExpression<?> target = invocation.getTarget();
            if (!(target instanceof CtConstructorCall)) {
                return false;
            }
            
            CtConstructorCall<?> hexOfCall = (CtConstructorCall<?>) target;
            CtTypeReference<?> hexOfType = hexOfCall.getType();
            if (hexOfType == null || !"org.cactoos.text.HexOf".equals(hexOfType.getQualifiedName())) {
                return false;
            }
            
            // Check if HexOf is constructed with BytesOf
            List<CtExpression<?>> hexOfArgs = hexOfCall.getArguments();
            if (hexOfArgs.isEmpty() || !(hexOfArgs.get(0) instanceof CtConstructorCall)) {
                return false;
            }
            
            CtConstructorCall<?> bytesOfCall = (CtConstructorCall<?>) hexOfArgs.get(0);
            CtTypeReference<?> bytesOfType = bytesOfCall.getType();
            return bytesOfType != null && "org.cactoos.io.BytesOf".equals(bytesOfType.getQualifiedName());
        }
        
        @Override
        public void process(CtInvocation<?> invocation) {
            // Extract the byte array expression from BytesOf constructor
            CtConstructorCall<?> hexOfCall = (CtConstructorCall<?>) invocation.getTarget();
            CtConstructorCall<?> bytesOfCall = (CtConstructorCall<?>) hexOfCall.getArguments().get(0);
            CtExpression<?> byteArrayExpr = bytesOfCall.getArguments().get(0);
            
            // Replace with bytesToHex(byteArray) call
            // Note: The bytesToHex method should be added separately or exist in the project
            getFactory().Code().createCodeSnippetExpression(
                "bytesToHex(" + byteArrayExpr.toString() + ")"
            ).replace(invocation);
        }
    }
    
    /**
     * Replaces ListOf constructor calls with Arrays.asList.
     */
    public static class ListOfReplacementProcessor extends AbstractProcessor<CtConstructorCall<?>> {
        
        @Override
        public boolean isToBeProcessed(CtConstructorCall<?> constructorCall) {
            CtTypeReference<?> typeRef = constructorCall.getType();
            return typeRef != null && "org.cactoos.list.ListOf".equals(typeRef.getQualifiedName());
        }
        
        @Override
        public void process(CtConstructorCall<?> constructorCall) {
            List<CtExpression<?>> args = constructorCall.getArguments();
            
            // Build replacement expression
            StringBuilder replacement = new StringBuilder("java.util.Arrays.asList(");
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) {
                    replacement.append(", ");
                }
                replacement.append(args.get(i).toString());
            }
            replacement.append(")");
            
            getFactory().Code().createCodeSnippetExpression(
                replacement.toString()
            ).replace(constructorCall);
        }
    }
    
    /**
     * Utility method to convert bytes to hex (provided for reference).
     * Projects using this transformation should add this method or equivalent.
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/project/src/main/java");
            System.err.println();
            System.err.println("This transformation fixes breaking changes from cactoos removal in com.artipie:http v1.1.3:");
            System.err.println("1. Replaces HexOf(BytesOf(bytes)).asString() with bytesToHex(bytes)");
            System.err.println("2. Replaces ListOf with Arrays.asList");
            System.err.println("3. Projects need to add bytesToHex method: see Main.bytesToHex for implementation");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        System.out.println("Applying transformation to: " + sourceDir);
        System.out.println("Fixing cactoos removal breaking changes...");
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        
        // Add processors
        launcher.addProcessor(new HexBytesOfReplacementProcessor());
        launcher.addProcessor(new ListOfReplacementProcessor());
        
        // Run transformation
        launcher.run();
        
        // Output transformed code
        launcher.prettyprint();
        
        System.out.println("Transformation completed successfully!");
        System.out.println();
        System.out.println("IMPORTANT: After applying this transformation:");
        System.out.println("1. Add the following method to classes that used HexOf/BytesOf:");
        System.out.println("   private static String bytesToHex(byte[] bytes) {");
        System.out.println("       StringBuilder hex = new StringBuilder();");
        System.out.println("       for (byte b : bytes) {");
        System.out.println("           hex.append(String.format(\"%02x\", b));");
        System.out.println("       }");
        System.out.println("       return hex.toString();");
        System.out.println("   }");
        System.out.println("2. Add import java.util.Arrays if not already present");
        System.out.println("3. Compile and test your project");
    }
}