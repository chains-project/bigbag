package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Collections;

public class TSerializerRefactoring {

    /**
     * Processor to handle the breaking change in org.apache.thrift.TSerializer.
     * The constructors now throw a checked exception, so instantiations need to be
     * wrapped in a try-catch block if they aren't already.
     */
    public static class TSerializerConstructorProcessor extends AbstractProcessor<CtConstructorCall<?>> {

        @Override
        public boolean isToBeProcessed(CtConstructorCall<?> candidate) {
            // 1. Type Check (Defensive for NoClasspath)
            CtTypeReference<?> type = candidate.getType();
            if (type == null) return false;

            // Check if this is TSerializer
            if (!type.getQualifiedName().contains("org.apache.thrift.TSerializer") 
                && !"TSerializer".equals(type.getSimpleName())) {
                return false;
            }

            // 2. Parent Check
            // We can only easily wrap statements inside methods/blocks.
            // We cannot easily refactor field initializers (e.g., private TSerializer s = new TSerializer();)
            // without moving code to constructors, which is too risky for this rule.
            CtStatement parentStatement = candidate.getParent(CtStatement.class);
            if (parentStatement == null) {
                return false;
            }

            // 3. Context Check
            // If the statement is already inside a try block, we might skip it (heuristic).
            // In a full AST, we would check if the catch clause catches TException, 
            // but in NoClasspath, looking at immediate structure is safer.
            CtElement strictParent = parentStatement.getParent();
            if (strictParent instanceof CtTry) {
                // It is likely already handled or being handled
                return false;
            }
            
            // If it is inside a block that is part of a Try, it might be handled, 
            // but ensuring a local try-catch is the safest transformation to guarantee compilation.
            
            return true;
        }

        @Override
        public void process(CtConstructorCall<?> ctorCall) {
            Factory factory = getFactory();
            CtStatement originalStatement = ctorCall.getParent(CtStatement.class);

            // Create the Exception Reference: org.apache.thrift.TException
            CtTypeReference<Throwable> exceptionRef = factory.Type().createReference("org.apache.thrift.TException");

            // Build the Try Block
            CtTry tryBlock = factory.Core().createTry();
            tryBlock.setBody(factory.Core().createBlock());
            
            // Move the original statement into the try block
            // We use clone to be safe, then replace the original
            tryBlock.getBody().addStatement(originalStatement.clone());

            // Build the Catch Block
            CtCatch catchBlock = factory.Core().createCatch();
            
            // Create "TException e"
            CtCatchVariable<Throwable> catchVar = factory.Code().createCatchVariable(exceptionRef, "e");
            catchBlock.setParameter(catchVar);
            catchBlock.setBody(factory.Core().createBlock());

            // Add e.printStackTrace() to catch block
            CtInvocation<?> printStackTrace = factory.Code().createInvocation(
                    factory.Code().createVariableRead(catchVar.getReference(), false),
                    factory.Method().createReference(
                            factory.Type().voidPrimitiveType(),
                            factory.Type().createReference(Throwable.class), // Owner
                            "printStackTrace",
                            Collections.emptyList(), // Param types
                            factory.Type().voidPrimitiveType() // Return type
                    )
            );
            catchBlock.getBody().addStatement(printStackTrace);

            // Assemble
            tryBlock.addCatcher(catchBlock);

            // Replace in source
            originalStatement.replace(tryBlock);
            
            System.out.println("Refactored TSerializer instantiation at line " + ctorCall.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/067f5d2c81ff87c90755f4ed48f62eb5faa8ecf9/singer/singer-commons/src/main/java/com/pinterest/singer/loggingaudit/client/AuditEventKafkaSender.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/067f5d2c81ff87c90755f4ed48f62eb5faa8ecf9/attempt_1/transformed";

        if (args.length > 0) inputPath = args[0];
        if (args.length > 1) outputPath = args[1];

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/067f5d2c81ff87c90755f4ed48f62eb5faa8ecf9/singer/singer-commons/src/main/java/com/pinterest/singer/loggingaudit/client/AuditEventKafkaSender.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/067f5d2c81ff87c90755f4ed48f62eb5faa8ecf9/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. NoClasspath Mode
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new TSerializerConstructorProcessor());

        try {
            launcher.run();
            System.out.println("Refactoring complete. Check output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}