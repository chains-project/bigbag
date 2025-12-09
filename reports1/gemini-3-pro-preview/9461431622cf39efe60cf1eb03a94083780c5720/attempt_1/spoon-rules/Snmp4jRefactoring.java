package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtTypedElement;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Collections;

/**
 * Refactoring script for SNMP4J Agent migration.
 * 
 * CHANGE ANALYSIS:
 * 1. org.snmp4j.agent.ManagedObject changed generic template (CLASS_GENERIC_TEMPLATE_CHANGED).
 *    Legacy code using raw 'ManagedObject' will trigger warnings or errors in newer versions 
 *    where 'ManagedObject<M extends MOScope>' is expected.
 * 2. Unchanged context: org.snmp4j.agent.MOScope is available.
 * 
 * STRATEGY:
 * Find all references to 'org.snmp4j.agent.ManagedObject'.
 * If they are raw (no type arguments), parameterize them with 'org.snmp4j.agent.MOScope'.
 * 
 * EXCLUSIONS:
 * - 'instanceof' checks (Java forbids generics in instanceof).
 * - Class literals (.class).
 * - Static member access.
 */
public class Snmp4jRefactoring {

    public static class ManagedObjectGenericProcessor extends AbstractProcessor<CtTypeReference<?>> {

        private static final String TARGET_CLASS = "org.snmp4j.agent.ManagedObject";
        private static final String TYPE_ARGUMENT = "org.snmp4j.agent.MOScope";

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // 1. Check Qualified Name (Robust string check for NoClasspath)
            if (candidate == null || !TARGET_CLASS.equals(candidate.getQualifiedName())) {
                return false;
            }

            // 2. Check if already generic
            if (!candidate.getActualTypeArguments().isEmpty()) {
                return false;
            }

            CtElement parent = candidate.getParent();

            // 3. Exclude 'instanceof' checks (e.g. obj instanceof ManagedObject) -> generics illegal here
            if (parent instanceof CtBinaryOperator) {
                CtBinaryOperator<?> op = (CtBinaryOperator<?>) parent;
                if (op.getKind() == BinaryOperatorKind.INSTANCEOF) {
                    return false;
                }
            }

            // 4. Exclude Class Literals (ManagedObject.class) and Static Access
            if (parent instanceof CtTypeAccess) {
                return false;
            }

            // 5. Include Declarations (Fields, Variables, Parameters, Return Types)
            if (parent instanceof CtTypedElement) {
                // Ensure the candidate is actually the type of the element, not a generic bound of another type
                CtTypedElement<?> typedElement = (CtTypedElement<?>) parent;
                if (typedElement.getType() == candidate) {
                    return true;
                }
            }

            // 6. Include Implements/Extends clauses
            if (parent instanceof CtType) {
                CtType<?> typeDecl = (CtType<?>) parent;
                // Check if it's in super interfaces or super class
                if (typeDecl.getSuperInterfaces().contains(candidate) || typeDecl.getSuperClass() == candidate) {
                    return true;
                }
            }

            // 7. Include method return types explicitly if not caught by CtTypedElement check
            if (parent instanceof CtMethod) {
                if (((CtMethod<?>) parent).getType() == candidate) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public void process(CtTypeReference<?> typeRef) {
            Factory factory = getFactory();
            
            // Create the type reference for the generic argument <MOScope>
            CtTypeReference<?> scopeType = factory.Type().createReference(TYPE_ARGUMENT);

            // Add the generic argument to the ManagedObject reference
            // This transforms "ManagedObject" -> "ManagedObject<MOScope>"
            typeRef.setActualTypeArguments(Collections.singletonList(scopeType));

            System.out.println("Refactored ManagedObject to ManagedObject<MOScope> at " 
                + (typeRef.getPosition().isValidPosition() ? "line " + typeRef.getPosition().getLine() : "unknown location"));
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden)
        String inputPath = "/home/kth/Documents/last_transformer/output/9461431622cf39efe60cf1eb03a94083780c5720/snmpman/snmpman/src/main/java/com/oneandone/snmpman/SnmpmanAgent.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/9461431622cf39efe60cf1eb03a94083780c5720/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/9461431622cf39efe60cf1eb03a94083780c5720/snmpman/snmpman/src/main/java/com/oneandone/snmpman/SnmpmanAgent.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/9461431622cf39efe60cf1eb03a94083780c5720/attempt_1/transformed");

        // =========================================================
        // CRITICAL: SNIPER MODE FOR SOURCE PRESERVATION
        // =========================================================
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // Defensive: Handle missing libraries gracefully
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new ManagedObjectGenericProcessor());

        try {
            System.out.println("Starting SNMP4J Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Check output in: " + outputPath);
        } catch (Exception e) {
            System.err.println("Refactoring failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}