package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;
import java.util.List;

/**
 * Refactoring Processor for Apache MINA SslFilter changes.
 * 
 * CHANGE: METHOD org.apache.mina.filter.ssl.SslFilter.setUseClientMode(boolean) [REMOVED]
 * 
 * STRATEGY:
 * 1. Locate calls to setUseClientMode(boolean).
 * 2. Attempt to locate the variable declaration of the SslFilter instance.
 * 3. If found and initialized via constructor, inject the boolean argument into the constructor:
 *    From: SslFilter f = new SslFilter(ctx); f.setUseClientMode(true);
 *    To:   SslFilter f = new SslFilter(ctx, true);
 * 4. If the variable declaration cannot be resolved (e.g., fields, complex scope) or is not a constructor call,
 *    replace the method call with a FIXME comment to prevent compilation errors while alerting the developer.
 */
public class SslFilterRefactoring {

    public static class SslFilterProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Check Method Name
            if (!"setUseClientMode".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Check Argument Count
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Check Owner Type (Defensive for NoClasspath)
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType == null) {
                return false;
            }
            
            // Use loose matching to handle potentially unresolved types in NoClasspath mode
            String qualName = declaringType.getQualifiedName();
            return qualName != null && qualName.contains("SslFilter");
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtExpression<?> arg = invocation.getArguments().get(0);
            CtExpression<?> target = invocation.getTarget();

            boolean success = false;

            // Attempt Refactoring: Move argument to Constructor
            if (target instanceof CtVariableAccess) {
                try {
                    // Try to resolve declaration (Works if in same file/scope)
                    CtVariable<?> variableDecl = ((CtVariableAccess<?>) target).getVariable().getDeclaration();
                    
                    if (variableDecl instanceof CtLocalVariable) {
                        CtLocalVariable<?> localVar = (CtLocalVariable<?>) variableDecl;
                        CtExpression<?> initializer = localVar.getDefaultExpression();

                        if (initializer instanceof CtConstructorCall) {
                            CtConstructorCall<?> ctorCall = (CtConstructorCall<?>) initializer;
                            String ctorType = ctorCall.getType().getQualifiedName();
                            
                            if (ctorType != null && ctorType.contains("SslFilter")) {
                                List<CtExpression<?>> args = ctorCall.getArguments();
                                
                                // Case A: Default constructor used (new SslFilter(ctx)) -> Add arg
                                if (args.size() == 1) {
                                    ctorCall.addArgument(arg.clone());
                                    success = true;
                                } 
                                // Case B: Constructor with boolean already used -> Replace arg
                                else if (args.size() == 2) {
                                    args.get(1).replace(arg.clone());
                                    success = true;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Resolution failed (likely due to NoClasspath or scope issues). 
                    // Proceed to Fallback.
                }
            }

            if (success) {
                // Happy Path: Logic moved to constructor. Remove the now invalid setter call.
                invocation.delete();
                System.out.println("Refactored setUseClientMode via constructor injection at line " + invocation.getPosition().getLine());
            } else {
                // Fallback Path: Could not modify constructor safely.
                // Comment out the code to fix compilation and warn user.
                
                String argText = arg.toString();
                String originalStmt = invocation.toString();
                
                // Create a block comment explaining the change
                CtComment replacementComment = factory.Code().createComment(
                    "FIXME (MINA Migration): setUseClientMode removed. Pass '" + argText + "' in SslFilter constructor instead.\n" +
                    "// " + originalStmt,
                    CtComment.CommentType.BLOCK
                );
                
                invocation.replace(replacementComment);
                System.out.println("Commented out setUseClientMode at line " + invocation.getPosition().getLine() + " (Manual check required)");
            }
        }
    }

    public static void main(String[] args) {
        // User-configurable paths
        String inputPath = "/Users/frankreyesgarcia/Documents/WORK/PHD/Transformer/output/00a7cc31784ac4a9cc27d506a73ae589d6df36d6/quickfixj/quickfixj-core/src/main/java/quickfix/mina/initiator/IoSessionInitiator.java";
        String outputPath = "reports/gemini-3-pro-preview/00a7cc31784ac4a9cc27d506a73ae589d6df36d6/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource(inputPath);
        launcher.setSourceOutputDirectory(outputPath);

        // CRITICAL SETTINGS for Source Preservation
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer for high-fidelity code transformation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Robustness for missing dependencies
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new SslFilterProcessor());

        try {
            launcher.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}