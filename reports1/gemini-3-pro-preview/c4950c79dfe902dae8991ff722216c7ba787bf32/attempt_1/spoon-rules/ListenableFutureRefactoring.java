package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ListenableFutureRefactoring {

    public static class AddCallbackProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Method Name Check
            if (!"addCallback".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check (1 or 2 args)
            int argCount = candidate.getArguments().size();
            if (argCount != 1 && argCount != 2) {
                return false;
            }

            // 3. Owner Check (ListenableFuture)
            // Defensive check for NoClasspath environment
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType != null && !declaringType.getQualifiedName().contains("ListenableFuture")) {
                // If known type and not ListenableFuture, skip.
                // If unknown (<unknown>), we might process it if signatures match, but let's be strict on name if available.
                if (!declaringType.getQualifiedName().equals("<unknown>")) {
                    return false;
                }
            }
            
            // Further validation could check if the method is being called on an object that looks like a future
            // but we rely on the method name and arg structure for NoClasspath scenarios.
            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // We are transforming:
            // future.addCallback(success, failure) OR future.addCallback(callback)
            // into:
            // future.completable().whenComplete((res, ex) -> { ... })

            // 1. Create the .completable() bridge
            // Note: This assumes the user is on Spring 6 where ListenableFuture has .completable()
            // If the variable is already a CompletableFuture, .completable() might not exist, 
            // but the method addCallback wouldn't exist on CompletableFuture either.
            CtExpression<?> target = invocation.getTarget();
            CtInvocation<?> completableCall = factory.Code().createInvocation(
                    target != null ? target.clone() : null,
                    factory.Method().createReference(
                        factory.Type().createReference("java.util.concurrent.CompletableFuture"), 
                        factory.Type().createReference("java.util.concurrent.CompletableFuture"), 
                        "completable"
                    )
            );

            // 2. Create parameters for the new whenComplete lambda: (res, ex)
            CtParameter<Object> resParam = factory.Core().createParameter();
            resParam.setSimpleName("res");
            resParam.setType(factory.Type().objectType()); // Use Object/var for safety

            CtParameter<Throwable> exParam = factory.Core().createParameter();
            exParam.setSimpleName("ex");
            exParam.setType(factory.Type().createReference(Throwable.class));

            // 3. Construct the Body of the Lambda
            CtBlock<?> lambdaBody = factory.Core().createBlock();
            
            CtIf ifStatement = factory.Core().createIf();
            
            // Condition: ex != null
            CtBinaryOperator<Boolean> condition = factory.Code().createBinaryOperator(
                    factory.Code().createVariableRead(exParam.getReference(), false),
                    factory.Code().createLiteral(null),
                    BinaryOperatorKind.NE
            );
            ifStatement.setCondition(condition);

            List<CtExpression<?>> args = invocation.getArguments();
            
            // Failure Block (Then part of IF)
            CtBlock<?> failureBlock = factory.Core().createBlock();
            CtExpression<?> failureArg = (args.size() == 2) ? args.get(1) : args.get(0);
            
            if (failureArg instanceof CtLambda) {
                // Inline lambda body: (ex) -> { ... }
                failureBlock = inlineLambda((CtLambda<?>) failureArg, exParam);
            } else {
                // Generate invocation: failureArg.onFailure(ex)
                CtInvocation<?> onFailureCall = factory.Code().createInvocation(
                        failureArg.clone(),
                        factory.Method().createReference(failureArg.getType(), factory.Type().voidPrimitiveType(), "onFailure"),
                        factory.Code().createVariableRead(exParam.getReference(), false)
                );
                failureBlock.addStatement(onFailureCall);
            }
            ifStatement.setThenStatement(failureBlock);

            // Success Block (Else part of IF)
            CtBlock<?> successBlock = factory.Core().createBlock();
            CtExpression<?> successArg = args.get(0);

            if (successArg instanceof CtLambda) {
                // Inline lambda body: (res) -> { ... }
                successBlock = inlineLambda((CtLambda<?>) successArg, resParam);
            } else {
                // Generate invocation: successArg.onSuccess(res)
                CtInvocation<?> onSuccessCall = factory.Code().createInvocation(
                        successArg.clone(),
                        factory.Method().createReference(successArg.getType(), factory.Type().voidPrimitiveType(), "onSuccess"),
                        factory.Code().createVariableRead(resParam.getReference(), false)
                );
                successBlock.addStatement(onSuccessCall);
            }
            ifStatement.setElseStatement(successBlock);

            lambdaBody.addStatement(ifStatement);

            // 4. Create the Lambda
            CtLambda<?> whenCompleteLambda = factory.Core().createLambda();
            whenCompleteLambda.setParameters(new ArrayList<>(List.of(resParam, exParam)));
            whenCompleteLambda.setBody(lambdaBody);

            // 5. Create the replacement invocation: .whenComplete(...)
            CtInvocation<?> replacement = factory.Code().createInvocation(
                    completableCall,
                    factory.Method().createReference(
                            factory.Type().createReference("java.util.concurrent.CompletableFuture"), 
                            factory.Type().voidPrimitiveType(), 
                            "whenComplete"
                    ),
                    whenCompleteLambda
            );

            // 6. Replace
            invocation.replace(replacement);
            System.out.println("Refactored ListenableFuture.addCallback at line " + invocation.getPosition().getLine());
        }

        /**
         * Helper to extract body from a lambda and rename parameters to match the new scope.
         */
        private CtBlock<?> inlineLambda(CtLambda<?> lambda, CtParameter<?> newParam) {
            Factory factory = lambda.getFactory();
            CtBlock<?> bodyBlock;
            
            if (lambda.getBody() != null) {
                bodyBlock = lambda.getBody().clone();
            } else if (lambda.getExpression() != null) {
                // Convert expression lambda x -> x.foo() to block { x.foo(); }
                bodyBlock = factory.Core().createBlock();
                bodyBlock.addStatement(factory.Code().createCodeSnippetStatement(lambda.getExpression().toString()));
                // Note: The snippet approach is safer for simple expressions in NoClasspath than constructing AST sometimes,
                // but let's try AST wrapping for robustness if possible. 
                // However, parameter renaming is hard on snippets.
                // Let's re-wrap expression into statement
                CtStatement stmt = null;
                // If it's an invocation, wrapped in implicit return?
                // For void compatibility in blocks, just add the expression as statement.
                // We use parsing for robustness or cloning.
                bodyBlock.addStatement(lambda.getExpression().clone());
            } else {
                return factory.Core().createBlock();
            }

            // Rename variables
            if (!lambda.getParameters().isEmpty()) {
                String oldName = lambda.getParameters().get(0).getSimpleName();
                String newName = newParam.getSimpleName();
                
                // Use Scanner to replace variable access
                bodyBlock.accept(new CtScanner() {
                    @Override
                    public <T> void visitCtVariableRead(CtVariableRead<T> variableRead) {
                        super.visitCtVariableRead(variableRead);
                        if (variableRead.getVariable().getSimpleName().equals(oldName)) {
                             // We can't easily change the reference because the declaration is gone.
                             // We change the name string directly if possible or creating a new VariableRead.
                             // In NoClasspath, references are loose.
                             variableRead.setVariable(newParam.getReference());
                        }
                    }
                });
            }
            return bodyBlock;
        }
    }

    public static void main(String[] args) {
        String inputPath = "/home/kth/Documents/last_transformer/output/c4950c79dfe902dae8991ff722216c7ba787bf32/future-converter/common-test/src/main/java/net/javacrumbs/futureconverter/common/test/spring/SpringConvertedFutureTestHelper.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/c4950c79dfe902dae8991ff722216c7ba787bf32/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/c4950c79dfe902dae8991ff722216c7ba787bf32/future-converter/common-test/src/main/java/net/javacrumbs/futureconverter/common/test/spring/SpringConvertedFutureTestHelper.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/c4950c79dfe902dae8991ff722216c7ba787bf32/attempt_1/transformed");

        // CRITICAL: Preserve Source Code structure
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // CRITICAL: Defensive mode
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new AddCallbackProcessor());
        
        try {
            launcher.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}