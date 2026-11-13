package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTargetedExpression;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-base.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        try {
            CtModel model = launcher.buildModel();
            int changes = 0;
            
            List<CtTypeReference<?>> typeRefs = model.getElements(new TypeFilter<>(CtTypeReference.class));
            for (CtTypeReference<?> ref : typeRefs) {
                String qualifiedName = ref.getQualifiedName();
                if (qualifiedName.equals("com.gargoylesoftware.htmlunit.ScriptResult")) {
                    CtTypeReference<?> newRef = launcher.getFactory().Type().createReference(
                        "org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult"
                    );
                    ref.replace(newRef);
                    changes++;
                    System.out.println("Replaced type reference: " + qualifiedName + " -> " + newRef.getQualifiedName());
                }
            }
            
            List<CtConstructorCall<?>> constructorCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class));
            for (CtConstructorCall<?> call : constructorCalls) {
                CtTypeReference<?> typeRef = call.getType();
                if (typeRef != null && typeRef.getQualifiedName() != null) {
                    String qualifiedName = typeRef.getQualifiedName();
                    if (qualifiedName.equals("com.gargoylesoftware.htmlunit.ScriptResult") ||
                        qualifiedName.equals("org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult")) {
                        
                        if (call.getArguments().size() == 1) {
                            CtExpression<?> arg = call.getArguments().get(0);
                            String argStr = arg.toString();
                            if (!argStr.endsWith(".toString()") && !argStr.endsWith(".toString()\"")) {
                                CtExecutableReference<?> toStringRef = launcher.getFactory().Executable().createReference(
                                    launcher.getFactory().Type().createReference("java.lang.String"),
                                    launcher.getFactory().Type().createReference("java.lang.String"),
                                    "toString"
                                );
                                CtInvocation<?> toStringInvocation = launcher.getFactory().Code().createInvocation(
                                    arg.clone(),
                                    toStringRef
                                );
                                call.getArguments().set(0, toStringInvocation);
                                changes++;
                                System.out.println("Added .toString() to constructor argument: " + call);
                            }
                        }
                    }
                }
            }
            
            List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));
            for (CtInvocation<?> invocation : invocations) {
                CtExecutableReference<?> execRef = invocation.getExecutable();
                if (execRef != null && "getJavaScriptResult".equals(execRef.getSimpleName())) {
                    CtExpression<?> target = invocation.getTarget();
                    if (target != null) {
                        String targetType = target.getType().toString();
                        if (targetType.contains("ScriptResult")) {
                            CtExecutableReference<?> toStringRef = launcher.getFactory().Executable().createReference(
                                launcher.getFactory().Type().createReference("java.lang.String"),
                                launcher.getFactory().Type().createReference("java.lang.String"),
                                "toString"
                            );
                            CtInvocation<?> toStringInvocation = launcher.getFactory().Code().createInvocation(
                                target.clone(),
                                toStringRef
                            );
                            invocation.replace(toStringInvocation);
                            changes++;
                            System.out.println("Replaced getJavaScriptResult() with toString(): " + invocation);
                        }
                    }
                }
            }
            
            List<CtCompilationUnit> compilationUnits = model.getElements(new TypeFilter<>(CtCompilationUnit.class));
            for (CtCompilationUnit cu : compilationUnits) {
                for (CtImport ctImport : cu.getImports()) {
                    if (ctImport.getReference() instanceof CtTypeReference) {
                        CtTypeReference<?> typeRef = (CtTypeReference<?>) ctImport.getReference();
                        String qualifiedName = typeRef.getQualifiedName();
                        if (qualifiedName.equals("com.gargoylesoftware.htmlunit.ScriptResult")) {
                            CtTypeReference<?> newRef = launcher.getFactory().Type().createReference(
                                "org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult"
                            );
                            ctImport.setReference(newRef);
                            changes++;
                            System.out.println("Updated import statement in: " + cu.getFile().getName());
                        }
                    }
                }
            }
            
            System.out.println("\nTransformation summary:");
            System.out.println("Total changes made: " + changes);
            
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            
            System.out.println("\nTransformation completed successfully!");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}