package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {

    private static final String REMOVED_TYPE = "jakarta.servlet.http.HttpSessionContext";
    private static final String OWNER_TYPE = "jakarta.servlet.http.HttpSession";
    private static final String[] REMOVED_METHODS = {
        "getSessionContext",
        "getValue",
        "getValueNames",
        "putValue",
        "removeValue"
    };

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: java github.chains.Main <input-source-dir> [output-dir]");
            System.exit(1);
        }

        Path inputDir = Paths.get(args[0]);
        Path outputDir = args.length == 2 ? Paths.get(args[1]) : inputDir.resolveSibling(inputDir.getFileName() + "-fixed");
        transform(inputDir.toFile(), outputDir.toFile());
    }

    public static void transform(File inputDir, File outputDir) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPreserveLineNumbers(true);
        launcher.addInputResource(inputDir.getAbsolutePath());
        launcher.setSourceOutputDirectory(outputDir);
        launcher.addProcessor(new RemovedServletApiProcessor());
        launcher.run();
    }

    private static final class RemovedServletApiProcessor extends AbstractProcessor<CtElement> {

        @Override
        public boolean isToBeProcessed(CtElement candidate) {
            return candidate instanceof CtImport || candidate instanceof CtMethod;
        }

        @Override
        public void process(CtElement element) {
            if (element instanceof CtImport ctImport) {
                if (isRemovedType(ctImport.getReference())) {
                    ctImport.delete();
                }
                return;
            }

            if (element instanceof CtMethod<?> method && shouldRemoveMethod(method)) {
                method.delete();
            }
        }

        private boolean shouldRemoveMethod(CtMethod<?> method) {
            if (!isRemovedMethodName(method.getSimpleName())) {
                return false;
            }
            if (!method.getParameters().isEmpty()) {
                return false;
            }

            CtTypeReference<?> returnType = method.getType();
            if (!isRemovedType(returnType)) {
                return false;
            }

            CtType<?> declaringType = method.getDeclaringType();
            return declaringType != null && implementsRemovedHttpSessionInterface(declaringType);
        }

        private boolean implementsRemovedHttpSessionInterface(CtType<?> type) {
            if (type == null) {
                return false;
            }
            for (CtTypeReference<?> superInterface : type.getSuperInterfaces()) {
                if (OWNER_TYPE.equals(superInterface.getQualifiedName())) {
                    return true;
                }
            }

            CtTypeReference<?> superClass = type.getSuperclass();
            if (superClass != null && OWNER_TYPE.equals(superClass.getQualifiedName())) {
                return true;
            }

            return false;
        }

        private boolean isRemovedType(spoon.reflect.reference.CtReference reference) {
            return reference instanceof CtTypeReference<?> typeReference
                && REMOVED_TYPE.equals(typeReference.getQualifiedName());
        }

        private boolean isRemovedMethodName(String methodName) {
            for (String removedMethod : REMOVED_METHODS) {
                if (removedMethod.equals(methodName)) {
                    return true;
                }
            }
            return false;
        }
    }
}
