package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.Iterator;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/jcabi-simpledb/src/test/java/com/jcabi/simpledb/RegionITCase.java");
        launcher.setSourceOutputDirectory("/workspace/jcabi-simpledb/src/test/java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.buildModel();

        // Replace Tv.TEN and Tv.EIGHT
        launcher.getModel().getElements(new TypeFilter<>(CtFieldRead.class)).forEach(fieldRead -> {
            String target = fieldRead.getTarget().toString();
            if (target.equals("Tv") || target.endsWith(".Tv")) {
                String fieldName = fieldRead.getVariable().getSimpleName();
                if ("TEN".equals(fieldName)) {
                    fieldRead.replace(launcher.getFactory().Code().createLiteral(10));
                } else if ("EIGHT".equals(fieldName)) {
                    fieldRead.replace(launcher.getFactory().Code().createLiteral(8));
                }
            }
        });

        // Remove import com.jcabi.aspects.Tv
        for (CtCompilationUnit cu : launcher.getModel().getElements(new TypeFilter<>(CtCompilationUnit.class))) {
            Iterator<CtImport> it = cu.getImports().iterator();
            while (it.hasNext()) {
                CtImport imp = it.next();
                if (imp.getReference() != null && "com.jcabi.aspects.Tv".equals(imp.getReference().toString())) {
                    it.remove();
                }
            }
        }

        launcher.prettyprint();
    }
}
