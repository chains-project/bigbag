package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String REMOVED_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String METHOD_NAME = "getJavaScriptResult";

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <source-root> [output-root]");
        }

        Path input = Paths.get(args[0]).toAbsolutePath().normalize();
        Path output = args.length == 2
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : input;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.buildModel();

        replaceJavaScriptResultCalls(launcher);
        replaceScriptResultDeclarations(launcher);
        removeScriptResultImports(launcher);

        launcher.prettyprint();
    }

    private static void replaceJavaScriptResultCalls(Launcher launcher) {
        for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            if (!METHOD_NAME.equals(invocation.getExecutable().getSimpleName()) || invocation.getArguments().size() != 0) {
                continue;
            }

            CtExpression<?> target = invocation.getTarget();
            if (target == null) {
                continue;
            }

            if (target instanceof CtConstructorCall<?> && isRemovedType(((CtConstructorCall<?>) target).getType())
                    && !((CtConstructorCall<?>) target).getArguments().isEmpty()) {
                invocation.replace((CtExpression<?>) ((CtConstructorCall<?>) target).getArguments().get(0).clone());
            }
            else {
                invocation.replace(target.clone());
            }
        }
    }

    private static void replaceScriptResultDeclarations(Launcher launcher) {
        for (CtLocalVariable<?> variable : launcher.getModel().getElements(new TypeFilter<>(CtLocalVariable.class))) {
            replaceVariableDeclaration(variable);
        }
        for (CtField<?> field : launcher.getModel().getElements(new TypeFilter<>(CtField.class))) {
            replaceVariableDeclaration(field);
        }
    }

    private static void removeScriptResultImports(Launcher launcher) {
        for (CtCompilationUnit compilationUnit : launcher.getModel().getAllTypes().stream()
                .map(type -> type.getPosition() == null ? null : type.getPosition().getCompilationUnit())
                .filter(unit -> unit != null)
                .distinct()
                .toList()) {
            compilationUnit.getImports().removeIf(Main::isRemovedImport);
        }
    }

    private static boolean isRemovedImport(CtImport ctImport) {
        return ctImport.getReference() instanceof CtTypeReference
                && REMOVED_TYPE.equals(((CtTypeReference<?>) ctImport.getReference()).getQualifiedName());
    }

    private static void replaceVariableDeclaration(CtVariable<?> variable) {
        if (!isRemovedType(variable.getType())) {
            return;
        }

        variable.setType(variable.getFactory().Type().OBJECT);

        CtExpression<?> defaultExpression = variable instanceof CtLocalVariable
                ? ((CtLocalVariable<?>) variable).getDefaultExpression()
                : ((CtField<?>) variable).getDefaultExpression();

        if (!(defaultExpression instanceof CtConstructorCall<?>)) {
            return;
        }

        CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) defaultExpression;
        if (!isRemovedType(constructorCall.getType()) || constructorCall.getArguments().isEmpty()) {
            return;
        }

        if (variable instanceof CtLocalVariable) {
            ((CtLocalVariable) variable).setDefaultExpression(constructorCall.getArguments().get(0).clone());
        }
        else {
            ((CtField) variable).setDefaultExpression(constructorCall.getArguments().get(0).clone());
        }
    }

    private static boolean isRemovedType(CtTypeReference<?> type) {
        return type != null && REMOVED_TYPE.equals(type.getQualifiedName());
    }
}
