package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String OLD_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
    private static final String SERVER_TYPE = "org.eclipse.jetty.server.Server";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("expected input source directory");
        }

        String inputDir = args[0];
        String outputDir = args.length > 1 ? args[1] : inputDir + "-fixed";

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addInputResource(inputDir);
        launcher.setSourceOutputDirectory(new File(outputDir));
        launcher.addProcessor(new ReplaceConnectorProcessor());
        launcher.run();
    }

    private static final class ReplaceConnectorProcessor extends AbstractProcessor<CtElement> {
        @Override
        public void process(CtElement element) {
            if (element instanceof CtCompilationUnit) {
                CtCompilationUnit compilationUnit = (CtCompilationUnit) element;
                List imports = new ArrayList(compilationUnit.getImports());
                boolean changed = false;
                for (Object item : imports) {
                    if (item instanceof CtImport) {
                        CtImport ctImport = (CtImport) item;
                        if (ctImport.toString().contains(OLD_CONNECTOR)) {
                            compilationUnit.getImports().remove(ctImport);
                            changed = true;
                        }
                    }
                }
                if (changed) {
                    compilationUnit.setImports(compilationUnit.getImports());
                }
                return;
            }

            if (element instanceof CtImport) {
                CtImport ctImport = (CtImport) element;
                if (ctImport.toString().contains(OLD_CONNECTOR)) {
                    ctImport.delete();
                }
                return;
            }

            if (!(element instanceof CtConstructorCall)) {
                return;
            }

            CtConstructorCall<?> call = (CtConstructorCall<?>) element;
            CtTypeReference<?> type = call.getType();
            if (type == null || !OLD_CONNECTOR.equals(type.getQualifiedName())) {
                return;
            }

            CtExpression<?> serverExpression = findServerExpression(call);
            if (serverExpression == null) {
                return;
            }

            CtConstructorCall<?> replacement = getFactory().Code().createConstructorCall(
                getFactory().Type().createReference(NEW_CONNECTOR),
                serverExpression.clone()
            );
            call.replace(replacement);
        }

        private CtExpression<?> findServerExpression(CtConstructorCall<?> call) {
            CtExecutable<?> executable = call.getParent(CtExecutable.class);
            if (executable != null) {
                for (CtParameter<?> parameter : executable.getParameters()) {
                    if (isServerType(parameter.getType())) {
                        return createVariableRead(parameter);
                    }
                }

                if (executable.getBody() != null) {
                    List<CtLocalVariable> locals = executable.getBody().getElements(new TypeFilter<>(CtLocalVariable.class));
                    for (CtLocalVariable<?> local : locals) {
                        if (isServerType(local.getType())) {
                            return createVariableRead(local);
                        }
                    }
                }
            }

            CtType<?> owner = call.getParent(CtType.class);
            if (owner != null) {
                for (CtField<?> field : owner.getFields()) {
                    if (isServerType(field.getType())) {
                        return createFieldRead(field);
                    }
                }
            }

            return null;
        }

        private boolean isServerType(CtTypeReference<?> type) {
            return type != null && SERVER_TYPE.equals(type.getQualifiedName());
        }

        private CtExpression<?> createVariableRead(CtVariable<?> variable) {
            return getFactory().Code().createCodeSnippetExpression(variable.getSimpleName());
        }

        private CtExpression<?> createFieldRead(CtField<?> field) {
            String access = field.hasModifier(ModifierKind.STATIC)
                ? field.getSimpleName()
                : "this." + field.getSimpleName();
            return getFactory().Code().createCodeSnippetExpression(access);
        }
    }
}
