package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class JRPenRefactoring {

    public static class LineWidthProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            if (!"setLineWidth".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Argument Analysis
            CtExpression<?> arg = candidate.getArguments().get(0);
            
            // Skip if explicitly null (valid for Float, invalid for float)
            if (arg instanceof CtLiteral && ((CtLiteral<?>) arg).getValue() == null) {
                return false;
            }

            CtTypeReference<?> argType = arg.getType();

            // If we know it's already a wrapper Float, skip.
            // If it's null (unknown/NoClasspath) or primitive, we process it.
            if (argType != null && "java.lang.Float".equals(argType.getQualifiedName())) {
                return false;
            }

            // 4. Owner Check (Defensive for NoClasspath)
            // The method belongs to JRPen or JRBasePen.
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            if (owner != null) {
                String ownerName = owner.getQualifiedName();
                // Check if owner is JRPen or one of its implementations
                if (!ownerName.contains("JRPen") && !ownerName.contains("JRBasePen") && !ownerName.equals("<unknown>")) {
                    return false;
                }
            }
            
            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtExpression<?> originalArg = invocation.getArguments().get(0);

            // Transformation: Explicitly box the float argument -> Float.valueOf(originalArg)
            // This ensures compatibility where the primitive signature was removed.
            
            CtTypeReference<?> floatClassRef = factory.Type().createReference("java.lang.Float");
            
            // Create invocation: Float.valueOf(arg)
            CtInvocation<?> boxingInvocation = factory.Code().createInvocation(
                factory.Code().createTypeAccess(floatClassRef),
                factory.Method().createReference(
                    floatClassRef, 
                    floatClassRef, // Return type is Float
                    "valueOf", 
                    factory.Type().floatPrimitiveType() // Parameter is float
                ),
                originalArg.clone()
            );

            originalArg.replace(boxingInvocation);
            System.out.println("Refactored JRPen.setLineWidth at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (can be modified or passed as args)
        String inputPath = "/Users/frankreyesgarcia/Documents/WORK/PHD/Transformer/output/0abf7148300f40a1da0538ab060552bca4a2f1d8/biapi/src/main/java/xdev/tableexport/export/ReportBuilder.java";
        String outputPath = "./reports/gemini-3-pro-preview/0abf7148300f40a1da0538ab060552bca4a2f1d8/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource(inputPath);
        launcher.setSourceOutputDirectory(outputPath);

        // CRITICAL SETTINGS for robust refactoring
        // 1. Enable comments preservation
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode to run without dependencies
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new LineWidthProcessor());
        
        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}