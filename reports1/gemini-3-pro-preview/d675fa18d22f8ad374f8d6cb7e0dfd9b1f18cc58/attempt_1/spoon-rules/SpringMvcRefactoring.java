package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SpringMvcRefactoring {

    /**
     * Processor 1: Migrates RequestMappingInfo constructors to the Builder API.
     * Spring 6 deprecates/modifies RequestMappingInfo constructors in favor of RequestMappingInfo.paths(...).build().
     */
    public static class RequestMappingInfoBuilderProcessor extends AbstractProcessor<CtConstructorCall<?>> {

        @Override
        public boolean isToBeProcessed(CtConstructorCall<?> candidate) {
            // Check if it is a constructor for RequestMappingInfo
            CtTypeReference<?> type = candidate.getType();
            if (type == null || !type.getQualifiedName().contains("RequestMappingInfo")) {
                return false;
            }

            // Target the common 7-8 argument constructor used in Spring 5
            // (name, patterns, methods, params, headers, consumes, produces, custom)
            return candidate.getArguments().size() >= 7;
        }

        @Override
        public void process(CtConstructorCall<?> ctorCall) {
            Factory factory = getFactory();
            List<CtExpression<?>> args = ctorCall.getArguments();

            // Mapping strategy based on standard Spring 5 constructor index:
            // 0: String name
            // 1: PatternsRequestCondition patterns
            // 2: RequestMethodsRequestCondition methods
            // 3: ParamsRequestCondition params
            // 4: HeadersRequestCondition headers
            // 5: ConsumesRequestCondition consumes
            // 6: ProducesRequestCondition produces
            // 7: RequestCondition<?> custom

            // The Builder API starts with static .paths(String...)
            // We need to try and unwrap "new PatternsRequestCondition(strings)" to just "strings"
            CtExpression<?> patternsArg = extractValueFromCondition(args.get(1));
            
            CtTypeReference<?> infoType = factory.Type().createReference("org.springframework.web.servlet.mvc.method.RequestMappingInfo");
            
            // 1. Create start of chain: RequestMappingInfo.paths(...)
            CtInvocation<?> chain = factory.Code().createInvocation(
                factory.Code().createTypeAccess(infoType),
                factory.Method().createReference(infoType, infoType, "paths", factory.Type().objectType()), // Simplify signature for generation
                patternsArg
            );

            // 2. Chain methods(...)
            chain = addToChain(chain, "methods", extractValueFromCondition(args.get(2)));

            // 3. Chain params(...)
            chain = addToChain(chain, "params", extractValueFromCondition(args.get(3)));

            // 4. Chain headers(...)
            chain = addToChain(chain, "headers", extractValueFromCondition(args.get(4)));

            // 5. Chain consumes(...)
            chain = addToChain(chain, "consumes", extractValueFromCondition(args.get(5)));

            // 6. Chain produces(...)
            chain = addToChain(chain, "produces", extractValueFromCondition(args.get(6)));

            // 7. Chain customCondition(...)
            if (args.size() > 7) {
                chain = addToChain(chain, "customCondition", args.get(7));
            }

            // 8. Chain mappingName(...) - Argument 0
            // Note: mappingName is often last in builder chains or part of options, 
            // but effectively available on the builder in newer versions.
            chain = addToChain(chain, "mappingName", args.get(0));

            // 9. .build()
            CtInvocation<?> finalBuild = factory.Code().createInvocation(
                chain,
                factory.Method().createReference(chain.getType(), infoType, "build")
            );

            // Replace the constructor call
            ctorCall.replace(finalBuild);
            System.out.println("Refactored RequestMappingInfo constructor at line " + ctorCall.getPosition().getLine());
        }

        // Helper to append a method call to the builder chain
        private CtInvocation<?> addToChain(CtInvocation<?> target, String methodName, CtExpression<?> arg) {
            if (arg == null || isNullLiteral(arg)) {
                return target; // Skip null arguments to keep builder clean
            }
            return getFactory().Code().createInvocation(
                target,
                getFactory().Method().createReference(target.getType(), target.getType(), methodName, arg.getType()),
                arg.clone()
            );
        }

        private boolean isNullLiteral(CtExpression<?> arg) {
            return arg instanceof CtLiteral && ((CtLiteral<?>) arg).getValue() == null;
        }

        /**
         * Heuristic: If the argument is 'new SomeCondition(value)', return 'value'.
         * Otherwise, return the argument as-is (defensive).
         */
        private CtExpression<?> extractValueFromCondition(CtExpression<?> arg) {
            if (arg instanceof CtConstructorCall) {
                CtConstructorCall<?> call = (CtConstructorCall<?>) arg;
                CtTypeReference<?> type = call.getType();
                if (type != null && type.getSimpleName().endsWith("Condition")) {
                    if (!call.getArguments().isEmpty()) {
                        return call.getArguments().get(0);
                    }
                }
            }
            return arg;
        }
    }

    /**
     * Processor 2: Handles RequestMappingHandlerMapping changes.
     * Removes calls to setUseSuffixPatternMatch and setUseTrailingSlashMatch which were removed in Spring 6.
     */
    public static class LegacyHandlerMappingCleaner extends AbstractProcessor<CtInvocation<?>> {
        private static final List<String> REMOVED_METHODS = Arrays.asList(
            "setUseSuffixPatternMatch",
            "setUseTrailingSlashMatch",
            "setUseRegisteredSuffixPatternMatch"
        );

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            String methodName = candidate.getExecutable().getSimpleName();
            if (!REMOVED_METHODS.contains(methodName)) {
                return false;
            }

            CtExpression<?> target = candidate.getTarget();
            if (target == null) return false;

            CtTypeReference<?> type = target.getType();
            // Defensive: type might be null in NoClasspath
            return type != null && type.getQualifiedName().contains("RequestMappingHandlerMapping");
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // These methods are removed. The refactoring strategy is to remove the configuration 
            // as these features are no longer supported or enabled by default in the same way.
            
            // If the invocation is a standalone statement, remove it.
            CtElement parent = invocation.getParent();
            if (parent instanceof CtBlock) {
                invocation.delete();
            } else if (parent instanceof CtStatement) {
                parent.delete();
            } else {
                // If it's part of a fluent chain or other expression, replace with null or comment?
                // Usually these are void setters, so they are statements.
                invocation.replace(getFactory().Code().createComment("TODO: " + invocation.getExecutable().getSimpleName() + " was removed in Spring 6", CtComment.CommentType.BLOCK));
            }
            System.out.println("Removed deprecated configuration: " + invocation.getExecutable().getSimpleName());
        }
    }

    public static void main(String[] args) {
        String inputPath = "/home/kth/Documents/last_transformer/output/d675fa18d22f8ad374f8d6cb7e0dfd9b1f18cc58/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/EndpointService.java"; // User configurable
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/d675fa18d22f8ad374f8d6cb7e0dfd9b1f18cc58/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/d675fa18d22f8ad374f8d6cb7e0dfd9b1f18cc58/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/EndpointService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/d675fa18d22f8ad374f8d6cb7e0dfd9b1f18cc58/attempt_1/transformed");

        // 1. Enable comments to preserve manual TODOs
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. CRITICAL: Force Sniper Printer for high-fidelity source preservation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Defensive NoClasspath mode
        launcher.getEnvironment().setNoClasspath(true);

        // Add Processors
        launcher.addProcessor(new RequestMappingInfoBuilderProcessor());
        launcher.addProcessor(new LegacyHandlerMappingCleaner());

        try {
            launcher.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}