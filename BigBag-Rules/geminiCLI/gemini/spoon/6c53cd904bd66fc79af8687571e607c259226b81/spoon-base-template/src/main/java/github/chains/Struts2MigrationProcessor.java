package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Struts2MigrationProcessor extends AbstractProcessor<CtCompilationUnit> {
    @Override
    public void process(CtCompilationUnit compilationUnit) {
        // 1. Update references within the types
        if (compilationUnit.getDeclaredTypes() != null) {
            compilationUnit.getDeclaredTypes().forEach(type -> {
                type.getElements(new TypeFilter<>(CtTypeReference.class)).forEach(ref -> {
                    if ("org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter".equals(ref.getQualifiedName())) {
                        ref.setPackage(getFactory().Package().getOrCreate("org.apache.struts2.dispatcher.filter").getReference());
                    }
                });
            });
        }

        // 2. Remove old imports
        Collection<CtImport> imports = compilationUnit.getImports();
        List<CtImport> toRemove = new ArrayList<>();
        for (CtImport imp : imports) {
            if (imp.getReference() != null && 
                "org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter".equals(imp.getReference().toString())) {
                toRemove.add(imp);
            }
        }
        
        if (!toRemove.isEmpty()) {
             List<CtImport> newImports = new ArrayList<>(imports);
             newImports.removeAll(toRemove);
             compilationUnit.setImports(newImports);
        }
    }
}