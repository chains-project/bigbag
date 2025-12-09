package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class Snmp4jRefactoring {

    /**
     * Processor to handle the breaking change:
     * CLASS org.snmp4j.agent.ManagedObject changes=CLASS_GENERIC_TEMPLATE_CHANGED
     *
     * Strategy:
     * Identify raw usages of ManagedObject and parameterize them with <MOScope>.
     * This ensures source compatibility with the new generic definition.
     */
    public static class ManagedObjectGenericProcessor extends AbstractProcessor<CtTypeReference<?>> {

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // 1. Name Check (Fast fail)
            if (!"ManagedObject".equals(candidate.getSimpleName())) {
                return false;
            }

            // 2. Package/Import Check (Defensive for NoClasspath)
            // Accepts fully qualified names or simple names if resolution is incomplete
            String qName = candidate.getQualifiedName();
            boolean isTarget = qName.equals("ManagedObject") 
                            || qName.contains("org.snmp4j.agent.ManagedObject");
            
            if (!isTarget) {
                return false;
            }

            // 3. Generics Check: Only process if it is currently RAW (no type arguments)
            if (!candidate.getActualTypeArguments().isEmpty()) {
                return false;
            }

            // 4. Context Safety Checks
            // Do not process imports themselves
            if (candidate.getParent() instanceof CtImport) {
                return false;
            }
            // Do not process class literals (ManagedObject.class) or static field access
            // These cannot be parameterized in Java
            if (candidate.getParent() instanceof CtTypeAccess) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtTypeReference<?> candidate) {
            // Transformation: Add <org.snmp4j.agent.MOScope> as a type argument
            // We use FQN to avoid ambiguous resolution issues in NoClasspath mode
            CtTypeReference<?> scopeType = getFactory().Type().createReference("org.snmp4j.agent.MOScope");
            
            try {
                candidate.addActualTypeArgument(scopeType);
                
                String position = candidate.getPosition().isValidPosition() 
                    ? "line " + candidate.getPosition().getLine() 
                    : "unknown location";
                System.out.println("Refactored: ManagedObject -> ManagedObject<MOScope> at " + position);
            } catch (Exception e) {
                System.err.println("Failed to refactor usage at " + candidate);
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        // Default paths (can be modified by arguments or hardcoded)
        String inputPath = "/home/kth/Documents/last_transformer/output/7f7de81d28b68b091bef2e6f6ffd1836167be6ea/snmpman/snmpman/src/main/java/com/oneandone/snmpman/SnmpmanAgent.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/7f7de81d28b68b091bef2e6f6ffd1836167be6ea/attempt_1/transformed";

        if (args.length > 0) inputPath = args[0];
        if (args.length > 1) outputPath = args[1];

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/7f7de81d28b68b091bef2e6f6ffd1836167be6ea/snmpman/snmpman/src/main/java/com/oneandone/snmpman/SnmpmanAgent.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/7f7de81d28b68b091bef2e6f6ffd1836167be6ea/attempt_1/transformed");

        // CRITICAL SETTINGS for robust refactoring
        // 1. Enable comments preservation
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Handle execution without full dependencies
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new ManagedObjectGenericProcessor());

        try {
            System.out.println("Starting SNMP4J Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            System.err.println("Fatal error during refactoring:");
            e.printStackTrace();
        }
    }
}