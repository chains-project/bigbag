package github.chains;

import spoon.MavenLauncher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final String FLYWAY = "org.flywaydb.core.Flyway";

    public static void main(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: <input-src-dir> <output-src-dir>");
        }

        Path sourcePath = Paths.get(args[0]).toAbsolutePath();
        Path projectRoot = findProjectRoot(sourcePath);

        MavenLauncher launcher = new MavenLauncher(projectRoot.toString(), MavenLauncher.SOURCE_TYPE.APP_SOURCE);
        launcher.getEnvironment().setAutoImports(false);
        launcher.setSourceOutputDirectory(args[1]);
        launcher.addProcessor(new FlywayProcessor());
        launcher.run();
    }

    private static Path findProjectRoot(Path sourcePath) {
        Path current = Files.isDirectory(sourcePath) ? sourcePath : sourcePath.getParent();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalArgumentException("Could not locate pom.xml above " + sourcePath);
    }

    private static final class FlywayProcessor extends AbstractProcessor<CtElement> {
        @Override
        public boolean isToBeProcessed(CtElement candidate) {
            return true;
        }

        @Override
        public void process(CtElement element) {
            if (element instanceof CtLocalVariable<?> localVariable) {
                rewriteLocalVariable(localVariable);
            }
        }

        private void rewriteLocalVariable(CtLocalVariable<?> localVariable) {
            CtExpression<?> defaultExpression = localVariable.getDefaultExpression();
            if (!(defaultExpression instanceof CtConstructorCall<?> constructorCall)) {
                return;
            }
            if (!isFlywayType(constructorCall.getType())) {
                return;
            }

            CtBlock<?> block = localVariable.getParent(CtBlock.class);
            if (block == null) {
                return;
            }

            List<CtInvocation<?>> setters = new ArrayList<>();
            boolean seenLocal = false;
            for (CtStatement currentStatement : block.getStatements()) {
                if (!seenLocal) {
                    seenLocal = currentStatement == localVariable;
                    continue;
                }
                if (!(currentStatement instanceof CtInvocation<?> invocation)) {
                    break;
                }
                if (!isFlywaySetter(invocation) || !isInvocationOnVariable(invocation, localVariable.getSimpleName())) {
                    break;
                }
                setters.add(invocation);
            }

            StringBuilder rewritten = new StringBuilder("org.flywaydb.core.Flyway.configure");
            CtInvocation<?> classLoaderSetter = null;
            for (CtInvocation<?> setter : setters) {
                if ("setClassLoader".equals(setter.getExecutable().getSimpleName())) {
                    classLoaderSetter = setter;
                }
            }
            rewritten.append(classLoaderSetter == null ? "()" : "(" + classLoaderSetter.getArguments().get(0) + ")");
            for (CtInvocation<?> setter : setters) {
                if (setter == classLoaderSetter) {
                    continue;
                }
                String methodName = fluentName(setter.getExecutable().getSimpleName());
                rewritten.append('.').append(methodName).append('(');
                for (int i = 0; i < setter.getArguments().size(); i++) {
                    if (i > 0) {
                        rewritten.append(", ");
                    }
                    rewritten.append(setter.getArguments().get(i));
                }
                rewritten.append(')');
            }
            rewritten.append(".load()");

            localVariable.setDefaultExpression(getFactory().Code().createCodeSnippetExpression(rewritten.toString()));
            for (CtInvocation<?> setter : setters) {
                setter.delete();
            }
        }

        private boolean isFlywaySetter(CtInvocation<?> invocation) {
            String name = invocation.getExecutable().getSimpleName();
            return "setDataSource".equals(name) || "setLocations".equals(name)
                    || "setValidateOnMigrate".equals(name) || "setClassLoader".equals(name);
        }

        private boolean isInvocationOnVariable(CtInvocation<?> invocation, String variableName) {
            CtExpression<?> target = invocation.getTarget();
            return target instanceof CtVariableRead<?> read && variableName.equals(read.getVariable().getSimpleName());
        }

        private boolean isFlywayType(CtTypeReference<?> type) {
            if (type == null) {
                return false;
            }
            return FLYWAY.equals(type.getQualifiedName()) || "Flyway".equals(type.getSimpleName());
        }

        private String fluentName(String setterName) {
            if (setterName.startsWith("set") && setterName.length() > 3) {
                return Character.toLowerCase(setterName.charAt(3)) + setterName.substring(4);
            }
            return setterName;
        }
    }
}
