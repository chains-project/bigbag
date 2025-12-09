package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtNewArray;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.ArrayList;
import java.util.List;

/**
 * Refactoring script for Artipie library changes.
 * Based on the diff, this handles the migration of RsHasHeaders to use Iterable
 * instead of Arrays/Varargs, which is a common breaking change pattern associated
 * with the provided signatures.
 */
public class ArtipieMigration {

    public static class RsHasHeadersProcessor extends AbstractProcessor<CtConstructorCall<?>> {

        @Override
        public boolean isToBeProcessed(CtConstructorCall<?> candidate) {
            // 1. Check Constructor Name (Relaxed match for NoClasspath)
            CtTypeReference<?> type = candidate.getType();
            if (type == null || !type.getSimpleName().equals("RsHasHeaders")) {
                return false;
            }

            // 2. Check Package (Defensive)
            if (type.getPackage() != null && 
                !type.getPackage().getQualifiedName().startsWith("com.artipie.http")) {
                // If package is known and wrong, skip. If unknown, proceed cautiously.
                if (!type.getPackage().getQualifiedName().equals("<unknown>")) {
                    return false;
                }
            }

            // 3. Analyze Arguments to determine if refactoring is needed
            List<CtExpression<?>> args = candidate.getArguments();
            
            // Case A: Multiple arguments (Varargs usage)
            // ex: new RsHasHeaders(e1, e2) -> needs Arrays.asList(e1, e2)
            if (args.size() > 1) {
                return true;
            }

            // Case B: Single argument that is an Array
            // ex: new RsHasHeaders(new Entry[] { ... }) -> needs Arrays.asList(...)
            if (args.size() == 1) {
                CtExpression<?> arg = args.get(0);
                CtTypeReference<?> argType = arg.getType();

                // If explicitly an array creation
                if (arg instanceof CtNewArray) {
                    return true;
                }

                // If type is known to be an array
                if (argType != null && argType.isArray()) {
                    return true;
                }
                
                // If type is known to be Iterable/List/Collection, we SKIP (already fixed)
                if (argType != null && (
                    argType.getQualifiedName().contains("List") ||
                    argType.getQualifiedName().contains("Iterable") ||
                    argType.getQualifiedName().contains("Collection")
                )) {
                    return false;
                }
                
                // Fallback for NoClasspath: If we can't determine type, we assume safe
                // unless it looks like an array variable or the user explicitly wants this.
                // For safety, we skip ambiguous single arguments to avoid double wrapping.
            }

            return false;
        }

        @Override
        public void process(CtConstructorCall<?> ctor) {
            Factory factory = getFactory();
            List<CtExpression<?>> originalArgs = new ArrayList<>(ctor.getArguments());

            // Create reference to java.util.Arrays
            CtTypeReference<?> arraysRef = factory.Type().createReference("java.util.Arrays");
            
            // Create Arrays.asList(...) invocation
            CtInvocation<?> asListInvocation = factory.Code().createInvocation(
                factory.Code().createTypeAccess(arraysRef),
                factory.Method().createReference(
                    arraysRef,
                    factory.Type().listType(),
                    "asList",
                    factory.Type().objectType() // varargs Object...
                ),
                originalArgs // Pass all original arguments to asList
            );

            // Clear original arguments from the constructor call
            ctor.setArguments(new ArrayList<>());
            
            // Add the new wrapper argument
            ctor.addArgument(asListInvocation);

            System.out.println("Refactored RsHasHeaders at line " + ctor.getPosition().getLine() 
                + ": Wrapped " + originalArgs.size() + " argument(s) in Arrays.asList()");
        }
    }

    public static void main(String[] args) {
        // Default configuration
        String inputPath = "/home/kth/Documents/last_transformer/output/e36118e158965251089da1446777bd699d7473c1/files-adapter/src/test/java/com/artipie/files/FileProxySliceTest.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/e36118e158965251089da1446777bd699d7473c1/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/e36118e158965251089da1446777bd699d7473c1/files-adapter/src/test/java/com/artipie/files/FileProxySliceTest.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/e36118e158965251089da1446777bd699d7473c1/attempt_1/transformed");

        // CRITICAL: Preserve formatting and comments
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // Defensive: NoClasspath mode
        launcher.getEnvironment().setNoClasspath(true);

        // Add Processor
        launcher.addProcessor(new RsHasHeadersProcessor());

        try {
            System.out.println("Starting Artipie Migration Refactoring...");
            launcher.run();
            System.out.println("Refactoring Complete.");
        } catch (Exception e) {
            System.err.println("Error during refactoring:");
            e.printStackTrace();
        }
    }
}