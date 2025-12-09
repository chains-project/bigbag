package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class SnakeYamlConstructorRefactoring {

    /**
     * Processor to migrate org.yaml.snakeyaml.constructor.Constructor instantiations.
     * 
     * Changes:
     * - new Constructor() -> new Constructor(new LoaderOptions())
     * - new Constructor(Class) -> new Constructor(Class, new LoaderOptions())
     * - new Constructor(String) -> new Constructor(String, new LoaderOptions())
     * - new Constructor(TypeDescription) -> new Constructor(TypeDescription, new LoaderOptions())
     */
    public static class ConstructorProcessor extends AbstractProcessor<CtConstructorCall<?>> {
        
        @Override
        public boolean isToBeProcessed(CtConstructorCall<?> candidate) {
            // 1. Check Target Class
            CtTypeReference<?> type = candidate.getType();
            if (type == null) return false;
            
            // Use contains to handle fully qualified names or potential resolution issues
            String typeName = type.getQualifiedName();
            if (!typeName.equals("org.yaml.snakeyaml.constructor.Constructor") 
                && !typeName.equals("Constructor")) {
                return false;
            }

            int argCount = candidate.getArguments().size();

            // 2. Case: No-arg constructor (Always needs refactoring)
            if (argCount == 0) {
                return true;
            }

            // 3. Case: 1-arg constructor (Class, String, TypeDescription)
            // We must filter out the existing valid constructor: new Constructor(LoaderOptions)
            if (argCount == 1) {
                CtExpression<?> arg = candidate.getArguments().get(0);
                CtTypeReference<?> argType = arg.getType();

                // Defensive Check: If we can identify it's already LoaderOptions, skip it
                if (argType != null && argType.getQualifiedName().contains("LoaderOptions")) {
                    return false;
                }
                
                // Additional Defensive: Check the source code string representation if type resolution failed (NoClasspath)
                // If the argument looks like "new LoaderOptions()", skip it.
                if (arg.toString().contains("LoaderOptions")) {
                    return false;
                }

                // Otherwise, assume it is one of the removed constructors (Class, String, TypeDescription)
                return true;
            }

            return false;
        }

        @Override
        public void process(CtConstructorCall<?> ctorCall) {
            Factory factory = getFactory();

            // Create reference to org.yaml.snakeyaml.LoaderOptions
            CtTypeReference<?> loaderOptionsRef = factory.Type().createReference("org.yaml.snakeyaml.LoaderOptions");
            
            // Create the expression: new org.yaml.snakeyaml.LoaderOptions()
            // We use Fully Qualified Name to avoid import management issues in the target file
            CtConstructorCall<?> newLoaderOptionsExpr = factory.Code().createConstructorCall(loaderOptionsRef);

            // Add the new argument to the end of the argument list
            // For 0 args -> becomes (new LoaderOptions())
            // For 1 arg -> becomes (originalArg, new LoaderOptions())
            ctorCall.addArgument(newLoaderOptionsExpr);

            System.out.println("Refactored Constructor usage at line " + ctorCall.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/b1a941400d68445d76056ab8833cd6d2e3455954/fluxtion/compiler/src/test/java/com/fluxtion/compiler/builder/factory/GraphOfInstancesTest.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/b1a941400d68445d76056ab8833cd6d2e3455954/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/b1a941400d68445d76056ab8833cd6d2e3455954/fluxtion/compiler/src/test/java/com/fluxtion/compiler/builder/factory/GraphOfInstancesTest.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/b1a941400d68445d76056ab8833cd6d2e3455954/attempt_1/transformed");

        // CRITICAL SETTINGS for Spoon 11+ and formatting preservation
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Enable NoClasspath mode (defensive processing)
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new ConstructorProcessor());

        try {
            launcher.run();
            System.out.println("Refactoring complete. Check output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}