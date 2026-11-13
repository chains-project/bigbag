package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;

import java.io.File;
import java.nio.file.Path;

public class Main {
    private static final String OLD_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String OLD_METHOD = "getJavaScriptResult";

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: Main <input-source-dir> <output-source-dir>");
            System.exit(1);
        }

        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(new File(output.toString()));

        launcher.addProcessor(new AbstractProcessor<CtImport>() {
            @Override
            public void process(CtImport element) {
                CtReference reference = element.getReference();
                if (reference instanceof CtTypeReference<?> typeReference
                        && OLD_TYPE.equals(typeReference.getQualifiedName())) {
                    element.delete();
                }
            }
        });

        launcher.addProcessor(new AbstractProcessor<CtConstructorCall<?>>() {
            @Override
            public void process(CtConstructorCall<?> call) {
                if (isOldType(call.getType()) && !call.getArguments().isEmpty()) {
                    call.replace(call.getArguments().get(0).clone());
                }
            }
        });

        launcher.addProcessor(new AbstractProcessor<CtInvocation<?>>() {
            @Override
            public void process(CtInvocation<?> invocation) {
                if (OLD_METHOD.equals(invocation.getExecutable().getSimpleName())
                        && invocation.getTarget() instanceof CtConstructorCall<?> constructorCall
                        && isOldType(constructorCall.getType())
                        && !constructorCall.getArguments().isEmpty()) {
                    invocation.replace(constructorCall.getArguments().get(0).clone());
                }
            }
        });

        launcher.addProcessor(new AbstractProcessor<CtLocalVariable<?>>() {
            @Override
            public void process(CtLocalVariable<?> variable) {
                if (isOldType(variable.getType())) {
                    variable.setType(getFactory().Type().objectType());
                }
            }
        });

        launcher.addProcessor(new AbstractProcessor<CtField<?>>() {
            @Override
            public void process(CtField<?> field) {
                if (isOldType(field.getType())) {
                    field.setType(getFactory().Type().objectType());
                }
            }
        });

        launcher.addProcessor(new AbstractProcessor<CtParameter<?>>() {
            @Override
            public void process(CtParameter<?> parameter) {
                if (isOldType(parameter.getType())) {
                    parameter.setType(getFactory().Type().objectType());
                }
            }
        });

        launcher.addProcessor(new AbstractProcessor<CtMethod<?>>() {
            @Override
            public void process(CtMethod<?> method) {
                if (isOldType(method.getType())) {
                    method.setType(getFactory().Type().objectType());
                }
            }
        });

        launcher.run();
    }

    private static boolean isOldType(CtTypeReference<?> type) {
        return type != null && OLD_TYPE.equals(type.getQualifiedName());
    }
}
