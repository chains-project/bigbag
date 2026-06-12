package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.visitor.ImportCleaner;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/pdb/src/main/java/com/feedzai/commons/sql/abstraction/engine/impl/mysql/MySqlQueryExceptionHandler.java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        
        CtModel model = launcher.buildModel();
        
        for (CtTypeReference<?> ref : model.getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (ref.getQualifiedName().equals("com.mysql.jdbc.exceptions.MySQLTimeoutException")) {
                CtPackageReference pkgRef = launcher.getFactory().Core().createPackageReference();
                pkgRef.setSimpleName("com.mysql.cj.jdbc.exceptions");
                ref.setPackage(pkgRef);
            }
        }
        
        ImportCleaner cleaner = new ImportCleaner();
        for (CtType<?> type : model.getAllTypes()) {
            cleaner.process(type.getPosition().getCompilationUnit());
        }
        
        launcher.setSourceOutputDirectory("/workspace/pdb/src/main/java");
        launcher.prettyprint();
    }
}