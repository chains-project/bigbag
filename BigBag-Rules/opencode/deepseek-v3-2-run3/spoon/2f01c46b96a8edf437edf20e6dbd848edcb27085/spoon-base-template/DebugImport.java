package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.visitor.filter.TypeFilter;

public class DebugImport {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/depclean/depclean-maven-plugin/src/main/java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        
        CtModel model = launcher.buildModel();
        
        for (CtImport ctImport : model.getRootPackage().getElements(new TypeFilter<CtImport>(CtImport.class))) {
            System.out.println("Import: " + ctImport.getReference() + " - " + ctImport.getReference().getClass());
        }
    }
}
