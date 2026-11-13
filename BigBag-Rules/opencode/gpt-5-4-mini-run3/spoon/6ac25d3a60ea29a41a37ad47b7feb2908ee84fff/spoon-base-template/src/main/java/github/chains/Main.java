package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

public class Main {

    private static final String REMOVED_IMPORT_SIMPLE_NAME = "HttpSessionContext";
    private static final String REMOVED_SESSION_METHOD = "getSessionContext";
    private static final String REMOVED_VALUE_METHOD = "getValue";
    private static final String REMOVED_VALUE_NAMES_METHOD = "getValueNames";
    private static final String REMOVED_PUT_VALUE_METHOD = "putValue";
    private static final String REMOVED_REMOVE_VALUE_METHOD = "removeValue";

    private static boolean matchesRemovedImport(CtImport element) {
        return element != null
                && element.getReference() != null
                && REMOVED_IMPORT_SIMPLE_NAME.equals(element.getReference().getSimpleName());
    }

    private static boolean isHttpSessionImplementation(CtMethod<?> method) {
        CtType<?> declaringType = method.getDeclaringType();
        if (declaringType == null) {
            return false;
        }
        for (CtTypeReference<?> superInterface : declaringType.getSuperInterfaces()) {
            if (superInterface != null && "HttpSession".equals(superInterface.getSimpleName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesRemovedMethod(CtMethod<?> method) {
        if (!isHttpSessionImplementation(method)) {
            return false;
        }
        String name = method.getSimpleName();
        int parameterCount = method.getParameters().size();
        String returnType = method.getType() == null ? "" : method.getType().getSimpleName();
        return (REMOVED_SESSION_METHOD.equals(name) && parameterCount == 0 && REMOVED_IMPORT_SIMPLE_NAME.equals(returnType))
                || (REMOVED_VALUE_METHOD.equals(name) && parameterCount == 1 && "Object".equals(returnType))
                || (REMOVED_VALUE_NAMES_METHOD.equals(name) && parameterCount == 0 && "String[]".equals(returnType))
                || (REMOVED_PUT_VALUE_METHOD.equals(name) && parameterCount == 2 && "void".equals(returnType))
                || (REMOVED_REMOVE_VALUE_METHOD.equals(name) && parameterCount == 1 && "void".equals(returnType));
    }

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        Path input = Paths.get(args[0]);
        Path output = args.length == 2
                ? Paths.get(args[1])
                : input.resolveSibling(input.getFileName() + "-fixed");

        Launcher launcher = new Launcher();
        launcher.addInputResource(input.toString());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setCopyResources(false);
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.getEnvironment().setSourceOutputDirectory(output.toFile());

        launcher.addProcessor(new AbstractProcessor<CtCompilationUnit>() {
            @Override
            public boolean isToBeProcessed(CtCompilationUnit element) {
                for (CtImport ctImport : element.getImports()) {
                    if (matchesRemovedImport(ctImport)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void process(CtCompilationUnit element) {
                element.getImports().removeIf(Main::matchesRemovedImport);
            }
        });

        launcher.addProcessor(new AbstractProcessor<CtMethod<?>>() {
            @Override
            public boolean isToBeProcessed(CtMethod<?> element) {
                return matchesRemovedMethod(element);
            }

            @Override
            public void process(CtMethod<?> element) {
                element.delete();
            }
        });

        launcher.run();
    }
}
