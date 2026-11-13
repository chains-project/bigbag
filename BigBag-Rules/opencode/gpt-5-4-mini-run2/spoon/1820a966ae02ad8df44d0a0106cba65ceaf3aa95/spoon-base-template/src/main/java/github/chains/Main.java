package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtReturn;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.visitor.Filter;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;

public class Main {

    private static final String CONFIGURATOR_FQN = "ch.qos.logback.classic.spi.Configurator";
    private static final String CONFIGURATOR_STATUS_FQN = "ch.qos.logback.classic.spi.Configurator.ExecutionStatus";
    private static final String LOGGER_CONTEXT_FQN = "ch.qos.logback.classic.LoggerContext";
    private static final String METHOD_NAME = "configure";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: <input-source-dir> <output-source-dir>");
        }

        var inputDir = new File(args[0]);
        var outputDir = new File(args[1]);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.addInputResource(inputDir.getAbsolutePath());
        launcher.buildModel();

        CtTypeReference<?> statusType = launcher.getFactory().Type().createReference(CONFIGURATOR_STATUS_FQN);
        CtTypeReference<?> loggerContextType = launcher.getFactory().Type().createReference(LOGGER_CONTEXT_FQN);
        CtTypeReference<?> configuratorType = launcher.getFactory().Type().createReference(CONFIGURATOR_FQN);

        Set<String> changedTypes = new LinkedHashSet<>();
        List<CtMethod<?>> candidates = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtMethod.class)));
        for (CtMethod<?> method : candidates) {
            if (!shouldTransform(method, loggerContextType, configuratorType)) {
                continue;
            }
            rewriteMethod(method, statusType);
            if (method.getDeclaringType() != null) {
                changedTypes.add(method.getDeclaringType().getQualifiedName());
            }
        }

        if (changedTypes.isEmpty()) {
            return;
        }

        launcher.setOutputFilter((Filter<spoon.reflect.declaration.CtType<?>>) type -> changedTypes.contains(type.getQualifiedName()));
        launcher.setSourceOutputDirectory(outputDir);
        launcher.prettyprint();
    }

    private static boolean shouldTransform(CtMethod<?> method, CtTypeReference<?> loggerContextType, CtTypeReference<?> configuratorType) {
        if (!METHOD_NAME.equals(method.getSimpleName())) {
            return false;
        }
        if (method.getType() == null || !"void".equals(method.getType().getSimpleName())) {
            return false;
        }
        if (method.getParameters().size() != 1) {
            return false;
        }
        if (!loggerContextType.getQualifiedName().equals(method.getParameters().get(0).getType().getQualifiedName())) {
            return false;
        }
        if (method.getDeclaringType() != null && method.getDeclaringType().isSubtypeOf(configuratorType)) {
            return true;
        }
        return method.getBody() != null;
    }

    private static void rewriteMethod(CtMethod<?> method, CtTypeReference<?> statusType) {
        method.setType(statusType.clone());

        CtExpression<?> statusExpr = method.getFactory().Code()
                .createCodeSnippetExpression(statusType.getQualifiedName() + ".NEUTRAL");

        CtBlock<?> body = method.getBody();
        if (body == null) {
            return;
        }

        List<CtReturn<?>> returns = new ArrayList<>(body.getElements(new TypeFilter<>(CtReturn.class)));
        for (CtReturn<?> ctReturn : returns) {
            if (ctReturn.getReturnedExpression() == null) {
                ctReturn.setReturnedExpression((CtExpression) statusExpr.clone());
            }
        }

        if (body.getStatements().isEmpty()) {
            body.addStatement(method.getFactory().Code().createCtReturn((CtExpression) statusExpr.clone()));
            return;
        }

        var lastStatement = body.getLastStatement();
        if (!(lastStatement instanceof CtReturn)) {
            body.addStatement(method.getFactory().Code().createCtReturn((CtExpression) statusExpr.clone()));
        }
    }
}
