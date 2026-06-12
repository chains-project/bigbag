package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.cu.CompilationUnit;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) {
        processDirectory("/workspace/wicket-crudifier/src/main/java", "/workspace/wicket-crudifier/src/main/java");
        processDirectory("/workspace/wicket-crudifier/src/test/java", "/workspace/wicket-crudifier/src/test/java");
    }

    private static void processDirectory(String inputDir, String outputDir) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(inputDir);
        launcher.setSourceOutputDirectory(outputDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCopyResources(false);
        launcher.buildModel();

        // 1. Replace javax.validation -> jakarta.validation
        launcher.getModel().getElements(new TypeFilter<>(CtPackageReference.class)).forEach(pkg -> {
            if (pkg.getSimpleName().startsWith("javax.validation")) {
                pkg.setSimpleName(pkg.getSimpleName().replace("javax.validation", "jakarta.validation"));
            }
        });

        // 2. Replace HibernateValidatorProperty.validatorFactory.getValidator()
        launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class)).forEach(inv -> {
            if (inv.toString().contains("HibernateValidatorProperty.validatorFactory.getValidator()")) {
                CtExpression<?> newExpr = launcher.getFactory().Code().createCodeSnippetExpression("jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator()");
                inv.replace(newExpr);
            }
        });

        // 3. Remove old javax.validation imports and broken jakarta imports
        for (CompilationUnit cu : launcher.getFactory().CompilationUnit().getMap().values()) {
            cu.getImports().removeIf(imp -> imp.toString().contains("javax.validation") || imp.toString().equals("import jakarta.validation;"));
        }
        
        launcher.prettyprint();
    }
}
