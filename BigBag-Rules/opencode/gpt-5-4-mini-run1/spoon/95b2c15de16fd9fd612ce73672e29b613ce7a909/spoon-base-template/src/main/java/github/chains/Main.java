package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class Main {

    private static final String JAKARTA_PREFIX = "jakarta.servlet.";
    private static final String JAVA_PREFIX = "javax.servlet.";
    private static final String SELECT_CHANNEL_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String SERVER_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
    private static final String HTTP_CONFIGURATION = "org.eclipse.jetty.server.HttpConfiguration";
    private static final String HTTP_CONNECTION_FACTORY = "org.eclipse.jetty.server.HttpConnectionFactory";
    private static final String JETTY_CONNECTOR = "org.eclipse.jetty.server.Connector";

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: Main <input-source-dir> <output-source-dir>");
            System.exit(1);
        }

        File input = new File(args[0]);
        File output = new File(args[1]);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setComplianceLevel(11);
        launcher.addInputResource(input.getAbsolutePath());
        launcher.setSourceOutputDirectory(output);
        launcher.buildModel();

        Factory factory = launcher.getFactory();
        transformServletTypes(launcher, factory);
        transformJettyServerApi(launcher, factory);

        launcher.prettyprint();
        cleanupLegacyImports(output.toPath());
    }

    private static void transformServletTypes(Launcher launcher, Factory factory) {
        List<CtImport> imports = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtImport.class)));
        for (CtImport imp : imports) {
            if (!(imp.getReference() instanceof CtTypeReference)) {
                continue;
            }
            CtTypeReference<?> ref = (CtTypeReference<?>) imp.getReference();
            String qn = ref.getQualifiedName();
            if (qn != null && qn.startsWith(JAVA_PREFIX)) {
                imp.setReference(factory.Type().createReference(JAKARTA_PREFIX + qn.substring(JAVA_PREFIX.length())));
            }
        }

        List<CtTypeReference<?>> refs = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class)));
        for (CtTypeReference<?> ref : refs) {
            String qn = ref.getQualifiedName();
            if (qn != null && qn.startsWith(JAVA_PREFIX)) {
                ref.replace(factory.Type().createReference(JAKARTA_PREFIX + qn.substring(JAVA_PREFIX.length())));
            }
        }
    }

    private static void transformJettyServerApi(Launcher launcher, Factory factory) {
        List<CtInvocation<?>> invocations = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class)));
        List<CtConstructorCall<?>> constructors = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class)));
        Map<CtBlock<?>, CtLocalState> states = new HashMap<>();

        for (CtInvocation<?> invocation : invocations) {
            if (!isServerSetSendInvocation(invocation)) {
                continue;
            }

            CtBlock<?> block = invocation.getParent(CtBlock.class);
            if (block == null) {
                continue;
            }

            CtLocalState state = states.get(block);
            if (state == null) {
                state = new CtLocalState();
                state.serverExpression = cloneExpression(invocation.getTarget());
                state.configurationVar = createConfigurationVariable(factory);
                block.insertBegin(state.configurationVar);
                states.put(block, state);
            }

            invocation.setTarget(factory.Code().createVariableRead(state.configurationVar.getReference(), false));
        }

        for (CtConstructorCall<?> constructorCall : constructors) {
            if (!SELECT_CHANNEL_CONNECTOR.equals(qualifiedName(constructorCall.getType()))) {
                continue;
            }

            CtBlock<?> block = constructorCall.getParent(CtBlock.class);
            CtLocalState state = block == null ? null : states.get(block);
            if (state == null || state.configurationVar == null || state.serverExpression == null) {
                continue;
            }

            constructorCall.setType(factory.Type().createReference(SERVER_CONNECTOR));
            constructorCall.addArgument(state.serverExpression.clone());

            CtConstructorCall<?> httpFactory = factory.Code().createConstructorCall(factory.Type().createReference(HTTP_CONNECTION_FACTORY));
            httpFactory.addArgument(factory.Code().createVariableRead(state.configurationVar.getReference(), false));
            constructorCall.addArgument(httpFactory);

            upgradeConnectorDeclaration(constructorCall, factory);
        }

        for (CtConstructorCall<?> constructorCall : constructors) {
            if (!SERVER_CONNECTOR.equals(qualifiedName(constructorCall.getType()))) {
                continue;
            }
            CtAssignment<?, ?> assignment = constructorCall.getParent(CtAssignment.class);
            if (assignment != null) {
                CtExpression<?> lhs = assignment.getAssigned();
                if (lhs instanceof CtVariableAccess) {
                    CtVariableReference<?> ref = ((CtVariableAccess<?>) lhs).getVariable();
                    CtVariable<?> decl = ref == null ? null : ref.getDeclaration();
                    if (decl != null && decl.getType() != null && JETTY_CONNECTOR.equals(decl.getType().getQualifiedName())) {
                        decl.setType(factory.Type().createReference(SERVER_CONNECTOR));
                    }
                }
            }
        }

        List<CtField<?>> fields = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtField.class)));
        for (CtField<?> field : fields) {
            CtConstructorCall<?> init = field.getDefaultExpression() instanceof CtConstructorCall ? (CtConstructorCall<?>) field.getDefaultExpression() : null;
            if (init != null && SELECT_CHANNEL_CONNECTOR.equals(qualifiedName(init.getType()))) {
                field.setType(factory.Type().createReference(SERVER_CONNECTOR));
            }
        }

        List<CtLocalVariable<?>> locals = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtLocalVariable.class)));
        for (CtLocalVariable<?> local : locals) {
            CtConstructorCall<?> init = local.getDefaultExpression() instanceof CtConstructorCall ? (CtConstructorCall<?>) local.getDefaultExpression() : null;
            if (init != null && SELECT_CHANNEL_CONNECTOR.equals(qualifiedName(init.getType()))) {
                local.setType(factory.Type().createReference(SERVER_CONNECTOR));
            }
        }
    }

    private static boolean isServerSetSendInvocation(CtInvocation<?> invocation) {
        CtExpression<?> target = invocation.getTarget();
        CtTypeReference<?> targetType = target == null ? null : target.getType();
        String name = invocation.getExecutable() == null ? null : invocation.getExecutable().getSimpleName();
        return targetType != null
                && "org.eclipse.jetty.server.Server".equals(targetType.getQualifiedName())
                && ("setSendServerVersion".equals(name) || "setSendDateHeader".equals(name));
    }

    private static String qualifiedName(CtTypeReference<?> type) {
        return type == null ? null : type.getQualifiedName();
    }

    private static void upgradeConnectorDeclaration(CtConstructorCall<?> constructorCall, Factory factory) {
        CtVariable<?> declaration = constructorCall.getParent(CtVariable.class);
        if (declaration == null) {
            return;
        }
        CtTypeReference<?> type = declaration.getType();
        if (type != null && JETTY_CONNECTOR.equals(type.getQualifiedName())) {
            declaration.setType(factory.Type().createReference(SERVER_CONNECTOR));
        } else if (declaration instanceof CtField) {
            CtField<?> field = (CtField<?>) declaration;
            CtTypeReference<?> fieldType = field.getType();
            if (fieldType != null && JETTY_CONNECTOR.equals(fieldType.getQualifiedName())) {
                field.setType(factory.Type().createReference(SERVER_CONNECTOR));
            }
        }
    }

    private static void cleanupLegacyImports(Path outputDir) {
        try (Stream<Path> paths = Files.walk(outputDir)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::cleanupFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void cleanupFile(Path path) {
        try {
            String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            source = source.replace("import javax.servlet.ServletException;\n", "");
            source = source.replace("import javax.servlet.http.HttpServletRequest;\n", "");
            source = source.replace("import javax.servlet.http.HttpServletResponse;\n", "");
            source = source.replace("import org.eclipse.jetty.server.nio.SelectChannelConnector;\n", "");
            Files.write(path, source.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static CtLocalVariable<Object> createConfigurationVariable(Factory factory) {
        CtLocalVariable<Object> variable = factory.Core().createLocalVariable();
        variable.setSimpleName("httpConfiguration");
        variable.setType(factory.Type().createReference(HTTP_CONFIGURATION));
        CtConstructorCall<Object> initializer = factory.Code().createConstructorCall(factory.Type().createReference(HTTP_CONFIGURATION));
        variable.setDefaultExpression(initializer);
        return variable;
    }

    @SuppressWarnings("unchecked")
    private static <T extends CtExpression<?>> T cloneExpression(CtExpression<?> expression) {
        return expression == null ? null : (T) expression.clone();
    }

    private static final class CtLocalState {
        private CtLocalVariable<Object> configurationVar;
        private CtExpression<?> serverExpression;
    }
}
