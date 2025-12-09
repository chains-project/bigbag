package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;
import java.util.List;

public class SnakeYamlConstructorRefactoring {

    public static class ConstructorProcessor extends AbstractProcessor<CtConstructorCall<?>> {
        @Override
        public boolean isToBeProcessed(CtConstructorCall<?> candidate) {
            // 1. Type Check (Target specific class)
            CtTypeReference<?> type = candidate.getType();
            if (type == null) return false;

            // In NoClasspath, getQualifiedName() resolves based on imports.
            // We match strictly against the SnakeYAML Constructor class.
            String qName = type.getQualifiedName();
            if (!"org.yaml.snakeyaml.constructor.Constructor".equals(qName)) {
                return false;
            }

            // 2. Check if already refactored (Idempotency)
            // The strategy is to append `new LoaderOptions()` to the arguments.
            // We check if the last argument is already of type LoaderOptions.
            List<CtExpression<?>> args = candidate.getArguments();
            if (!args.isEmpty()) {
                CtExpression<?> lastArg = args.get(args.size() - 1);
                CtTypeReference<?> lastArgType = lastArg.getType();
                
                // Defensive check: if type is known and contains LoaderOptions, skip.
                // We use contains() to handle FQN variations or slight resolution diffs.
                if (lastArgType != null && lastArgType.getQualifiedName().contains("LoaderOptions")) {
                    return false;
                }
            }

            // If we are here, it matches the class name and doesn't have LoaderOptions at the end.
            // This covers all removed constructors (0-arg, 1-arg, 2-args) needing the update.
            return true;
        }

        @Override
        public void process(CtConstructorCall<?> candidate) {
            Factory factory = getFactory();
            
            // 1. Create reference for LoaderOptions
            CtTypeReference<?> loaderOptionsRef = factory.Type().createReference("org.yaml.snakeyaml.LoaderOptions");
            
            // 2. Create `new LoaderOptions()` expression
            CtConstructorCall<?> newLoaderOptions = factory.Code().createConstructorCall(loaderOptionsRef);
            
            // 3. Append to arguments
            // Example: new Constructor() -> new Constructor(new LoaderOptions())
            // Example: new Constructor(String) -> new Constructor(String, new LoaderOptions())
            candidate.addArgument(newLoaderOptions);
            
            System.out.println("Refactored SnakeYAML Constructor at line " + candidate.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/0a11c04038eae517540051dbf51f7f26b7221f20/simplelocalize-cli/src/main/java/io/simplelocalize/cli/configuration/ConfigurationLoader.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/0a11c04038eae517540051dbf51f7f26b7221f20/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/0a11c04038eae517540051dbf51f7f26b7221f20/simplelocalize-cli/src/main/java/io/simplelocalize/cli/configuration/ConfigurationLoader.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/0a11c04038eae517540051dbf51f7f26b7221f20/attempt_1/transformed");

        // CRITICAL SETTINGS for Spoon 11+ / Sniper
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new ConstructorProcessor());
        try { launcher.run(); } catch (Exception e) { e.printStackTrace(); }
    }
}