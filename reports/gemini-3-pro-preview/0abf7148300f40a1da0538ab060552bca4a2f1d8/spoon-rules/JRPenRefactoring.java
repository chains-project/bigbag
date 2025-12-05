package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class JRPenRefactoring {

    public static class JRPenProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            // The method setLineWidth(float) was removed, leaving setLineWidth(Float).
            if (!"setLineWidth".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Owner Check (Defensive for NoClasspath)
            // We check if the declaring type is related to JRPen or JRBasePen.
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            if (owner != null) {
                String ownerName = owner.getQualifiedName();
                // If the type is known and doesn't match the target classes, skip.
                // We permit "<unknown>" to handle NoClasspath scenarios where resolution fails.
                if (!ownerName.contains("JRPen") && 
                    !ownerName.contains("JRBasePen") && 
                    !ownerName.equals("<unknown>")) {
                    return false;
                }
            }

            // 4. Argument Type Check
            CtExpression<?> arg = candidate.getArguments().get(0);
            CtTypeReference<?> type = arg.getType();

            // If the argument is already a Boxed Float (java.lang.Float), we don't need to do anything.
            if (type != null && type.getQualifiedName().equals("java.lang.Float")) {
                return false;
            }

            // If the argument is definitely NOT a primitive (and not unknown), skip it.
            // We want to capture 'float' primitives or unknown types (likely literals/variables in NoClasspath).
            if (type != null && !type.isPrimitive() && !type.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtExpression<?> originalArg = invocation.getArguments().get(0);

            // Transformation: Explicitly box the argument using Float.valueOf(...)
            // This ensures compilation against the new API which only accepts java.lang.Float
            
            CtTypeReference<?> floatClass = factory.Type().createReference("java.lang.Float");
            CtTypeReference<Float> floatPrimitive = factory.Type().floatPrimitiveType();

            CtInvocation<?> boxing = factory.Code().createInvocation(
                factory.Code().createTypeAccess(floatClass),
                factory.Method().createReference(floatClass, floatClass, "valueOf", floatPrimitive),
                originalArg.clone()
            );

            originalArg.replace(boxing);
            System.out.println("Refactored setLineWidth at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/Users/frankreyesgarcia/Documents/WORK/PHD/Transformer/output/0abf7148300f40a1da0538ab060552bca4a2f1d8/biapi/src/main/java/xdev/tableexport/export/ReportBuilder.java";
        String outputPath = "./reports/gemini-3-pro-preview/0abf7148300f40a1da0538ab060552bca4a2f1d8/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource(inputPath);
        launcher.setSourceOutputDirectory(outputPath);

        // CRITICAL SETTINGS for robust refactoring
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Handle missing dependencies gracefully
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new JRPenProcessor());
        try { launcher.run(); } catch (Exception e) { e.printStackTrace(); }
    }
}