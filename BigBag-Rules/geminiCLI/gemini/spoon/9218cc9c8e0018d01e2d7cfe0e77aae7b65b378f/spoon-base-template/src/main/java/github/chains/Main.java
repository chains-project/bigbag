package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtTypeAccess;
import java.io.File;

public class Main {
    public static void main(String[] args) throws Exception {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/wicket-crudifier/src/main/java/com/premiumminds/wicket/crudifier/form/elements/ListControlGroups.java");
        launcher.addInputResource("/workspace/wicket-crudifier/src/test/java/com/premiumminds/wicket/crudifier/BootstrapCrudifierFormTest.java");
        
        File outputDir = new File("/workspace/spoon-base-template/target/spooned");
        launcher.setSourceOutputDirectory(outputDir);
        
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        
        CtModel model = launcher.buildModel();
        
        for (CtTypeReference<?> typeRef : model.getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (typeRef.toString().startsWith("javax.validation")) {
                String newName = typeRef.toString().replace("javax.validation", "jakarta.validation");
                typeRef.setSimpleName(newName.substring(newName.lastIndexOf('.') + 1));
                typeRef.setPackage(launcher.getFactory().Package().getOrCreate(newName.substring(0, newName.lastIndexOf('.'))).getReference());
            }
        }
        
        for (CtInvocation<?> inv : model.getElements(new TypeFilter<>(CtInvocation.class))) {
            if (inv.getExecutable().getSimpleName().equals("getValidator")) {
                CtExpression<?> target = inv.getTarget();
                if (target != null && target.toString().contains("validatorFactory")) {
                    // Create proper AST node instead of string snippet
                    CtTypeReference<?> validationType = launcher.getFactory().Type().createReference("jakarta.validation.Validation");
                    CtTypeAccess<?> typeAccess = launcher.getFactory().Code().createTypeAccess(validationType);
                    CtExecutableReference<Object> execRef = launcher.getFactory().Core().createExecutableReference();
                    execRef.setSimpleName("buildDefaultValidatorFactory");
                    execRef.setDeclaringType(validationType);
                    execRef.setStatic(true);
                    CtInvocation<?> newTarget = launcher.getFactory().Code().createInvocation(typeAccess, execRef);
                    inv.setTarget(newTarget);
                }
            }
        }
        
        for (spoon.reflect.declaration.CtCompilationUnit cu : launcher.getFactory().CompilationUnit().getMap().values()) {
            java.util.List<CtImport> newImports = new java.util.ArrayList<>();
            for (CtImport imp : cu.getImports()) {
                if (!imp.toString().contains("javax.validation")) {
                    newImports.add(imp);
                }
            }
            cu.setImports(newImports);
        }
        
        launcher.prettyprint();
    }
}
