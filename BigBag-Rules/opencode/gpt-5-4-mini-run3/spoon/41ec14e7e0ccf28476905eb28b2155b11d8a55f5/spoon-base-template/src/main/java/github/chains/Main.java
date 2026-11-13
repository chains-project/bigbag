package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import spoon.Launcher;
import spoon.compiler.Environment;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String TARGET_CONSTRUCTOR_TYPE = "com.premiumminds.webapp.wicket.validators.HibernateValidatorProperty";
    private static final String LEGACY_FIRST_ARGUMENT_TYPE = "java.lang.String";
    private static final String LEGACY_SECOND_ARGUMENT_TYPE = "org.apache.wicket.model.IModel";
    private static final String STRING_TYPE = "java.lang.String";
    private static final String MODEL_TYPE = "org.apache.wicket.model.IModel";

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: Main <input-source-dir> [output-source-dir]");
            System.exit(1);
        }

        Path inputSourceDir = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outputSourceDir = args.length == 2
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : inputSourceDir.resolveSibling(inputSourceDir.getFileName() + "-spoon-out");

        Launcher launcher = new Launcher();
        Environment environment = launcher.getEnvironment();
        environment.setNoClasspath(true);
        environment.setAutoImports(true);
        environment.setPreserveLineNumbers(true);
        environment.setCommentEnabled(true);

        launcher.addInputResource(inputSourceDir.toString());
        launcher.setSourceOutputDirectory(outputSourceDir.toFile());
        if (outputSourceDir.equals(inputSourceDir)) {
            launcher.getEnvironment().setCopyResources(false);
        }
        launcher.buildModel();

        for (CtConstructorCall<?> constructorCall : launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class))) {
            rewriteConstructorArguments(constructorCall);
        }

        launcher.prettyprint();
    }

    private static void rewriteConstructorArguments(CtConstructorCall<?> constructorCall) {
        CtTypeReference<?> declaringType = constructorCall.getExecutable() == null
                ? null
                : constructorCall.getExecutable().getDeclaringType();
        if (declaringType == null || !TARGET_CONSTRUCTOR_TYPE.equals(declaringType.getQualifiedName())) {
            return;
        }

        List<CtExpression<?>> arguments = new ArrayList<>(constructorCall.getArguments());
        if (arguments.size() != 2) {
            return;
        }

        if (!isType(arguments.get(0), LEGACY_FIRST_ARGUMENT_TYPE) || !isType(arguments.get(1), LEGACY_SECOND_ARGUMENT_TYPE)) {
            return;
        }

        List<CtExpression<?>> reorderedArguments = new ArrayList<>(2);
        reorderedArguments.add(arguments.get(1).clone());
        reorderedArguments.add(arguments.get(0).clone());
        constructorCall.setArguments(reorderedArguments);
    }

    private static boolean isType(CtExpression<?> expression, String qualifiedName) {
        if (expression == null || expression.getType() == null) {
            return false;
        }

        CtTypeReference<?> type = expression.getType();
        return qualifiedName.equals(type.getQualifiedName());
    }
}
