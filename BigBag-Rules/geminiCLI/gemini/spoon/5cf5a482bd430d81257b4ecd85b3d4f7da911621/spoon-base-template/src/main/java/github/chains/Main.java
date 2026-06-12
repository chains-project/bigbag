package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.reference.CtPackageReference;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/jakartaee-mvc-sample/src/main/java");
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.buildModel();

        for (CtTypeReference<?> typeRef : launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (typeRef.getPackage() != null && typeRef.getPackage().getQualifiedName().startsWith("javax.mvc")) {
                String newName = typeRef.getPackage().getQualifiedName().replaceFirst("^javax\\.mvc", "jakarta.mvc");
                typeRef.setPackage(launcher.getFactory().Package().createReference(newName));
            }
        }
        
        for (CtPackageReference pkg : launcher.getModel().getElements(new TypeFilter<>(CtPackageReference.class))) {
            if (pkg.getQualifiedName().startsWith("javax.mvc")) {
                String newName = pkg.getQualifiedName().replaceFirst("^javax\\.mvc", "jakarta.mvc");
                CtPackageReference newPkgRef = launcher.getFactory().Package().createReference(newName);
                pkg.replace(newPkgRef);
            }
        }

        for (CtCompilationUnit cu : launcher.getFactory().CompilationUnit().getMap().values()) {
            List<CtImport> newImports = new ArrayList<>();
            for (CtImport imp : cu.getImports()) {
                if (imp.getReference() != null && imp.getReference().toString().startsWith("javax.mvc")) {
                    String newRef = imp.getReference().toString().replaceFirst("^javax\\.mvc", "jakarta.mvc");
                    CtImport newImport = launcher.getFactory().Type().createImport(launcher.getFactory().Type().createReference(newRef));
                    newImports.add(newImport);
                } else {
                    newImports.add(imp);
                }
            }
            cu.setImports(newImports);
        }
        
        launcher.setSourceOutputDirectory("/workspace/jakartaee-mvc-sample/src/main/java-transformed");
        launcher.prettyprint();
    }
}
