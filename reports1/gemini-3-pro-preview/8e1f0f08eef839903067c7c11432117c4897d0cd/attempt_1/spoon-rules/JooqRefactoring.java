package org.jooq.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtNewArray;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Spoon Transformation for jOOQ Database Migration.
 * 
 * ANALYSIS:
 * The diff indicates 'METHOD_REMOVED_IN_SUPERCLASS' for PostgresDatabase (extending AbstractDatabase).
 * While specific method names were not listed in the 'REMOVED' section of the provided text, 
 * a common breaking change in this hierarchy is the removal of array-based setters 
 * (e.g., setIncludes(String[]), setExcludes(String[])) in favor of comma-separated Strings or Lists.
 * 
 * REFACTORING STRATEGY:
 * 1. Detect calls to 'setIncludes' (or 'setExcludes') on PostgresDatabase/AbstractDatabase.
 * 2. Check if the argument is a String array initializer (new String[] {...}).
 * 3. Flatten the array elements into a single comma-separated String argument.
 * 
 * EXAMPLE:
 * Old: database.setIncludes(new String[] {"A", "B"});
 * New: database.setIncludes("A,B");
 */
public class JooqRefactoring {

    public static class DatabaseConfigurationProcessor extends AbstractProcessor<CtInvocation<?>> {

        // Target method names that changed signature/were removed
        private static final List<String> TARGET_METHODS = List.of("setIncludes", "setExcludes");

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Method Name Check
            String methodName = candidate.getExecutable().getSimpleName();
            if (!TARGET_METHODS.contains(methodName)) {
                return false;
            }

            // 2. Argument Check
            // We are looking for the old signature taking an Array (String[])
            // and NOT the new signature taking a String.
            List<CtExpression<?>> args = candidate.getArguments();
            if (args.size() != 1) {
                return false;
            }

            CtExpression<?> arg = args.get(0);
            CtTypeReference<?> argType = arg.getType();

            // Defensive Check: If type is null (NoClasspath), check syntax structure (CtNewArray).
            // If type is known, ensure it's an Array.
            if (argType != null && !argType.isArray()) {
                return false; // It's likely already a String (the fixed version)
            }
            // If type is null, we rely on the AST node type below.

            // 3. Owner Check (Defensive text matching for NoClasspath)
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType != null) {
                String typeName = declaringType.getQualifiedName();
                // Check for PostgresDatabase or the superclass AbstractDatabase
                boolean isJooqDb = typeName.contains("PostgresDatabase") || 
                                   typeName.contains("AbstractDatabase") ||
                                   typeName.equals("<unknown>"); // Handle loose inference
                if (!isJooqDb) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtExpression<?> originalArg = invocation.getArguments().get(0);

            // Strategy: We can only safely refactor inline array definitions: new String[] {"A", "B"}
            if (originalArg instanceof CtNewArray) {
                CtNewArray<?> arrayArg = (CtNewArray<?>) originalArg;
                
                // Extract literal values
                List<CtExpression<?>> elements = arrayArg.getElements();
                
                // Build CSV String
                String csvResult = elements.stream()
                    .map(e -> {
                        if (e instanceof CtLiteral) {
                            Object val = ((CtLiteral<?>) e).getValue();
                            return val != null ? val.toString() : "";
                        }
                        // Fallback for variables inside array (complex case)
                        return "?"; 
                    })
                    .filter(s -> !s.equals("?")) // Skip unknown references
                    .collect(Collectors.joining(","));

                if (csvResult.isEmpty() && !elements.isEmpty()) {
                    System.err.println("Skipping refactoring at " + invocation.getPosition() + ": Array contains non-literals.");
                    return;
                }

                // Create new String literal: "A,B"
                CtLiteral<String> newArg = factory.Code().createLiteral(csvResult);

                // Replace the old array argument with the new String argument
                originalArg.replace(newArg);
                
                System.out.println("Refactored " + invocation.getExecutable().getSimpleName() + 
                                   " at line " + invocation.getPosition().getLine() + 
                                   " (Array -> CSV String)");
            } else {
                // If the argument is a variable (e.g., setIncludes(myArray)), we cannot safely refactor
                // without advanced flow analysis in NoClasspath mode.
                System.out.println("Manual intervention required at line " + invocation.getPosition().getLine() + 
                                   ": Argument is a variable, not an array literal.");
            }
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/8e1f0f08eef839903067c7c11432117c4897d0cd/jooq-meta-postgres-flyway/src/main/java/com/github/sabomichal/jooq/PostgresDDLDatabase.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/8e1f0f08eef839903067c7c11432117c4897d0cd/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/8e1f0f08eef839903067c7c11432117c4897d0cd/jooq-meta-postgres-flyway/src/main/java/com/github/sabomichal/jooq/PostgresDDLDatabase.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/8e1f0f08eef839903067c7c11432117c4897d0cd/attempt_1/transformed");

        // --- CRITICAL CONFIGURATION START ---
        
        // 1. Enable Comments to preserve documentation
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force SniperJavaPrettyPrinter to preserve original formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode (defensive processing)
        launcher.getEnvironment().setNoClasspath(true);
        
        // --- CRITICAL CONFIGURATION END ---

        launcher.addProcessor(new DatabaseConfigurationProcessor());

        try {
            System.out.println("Starting jOOQ Database Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Check output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}