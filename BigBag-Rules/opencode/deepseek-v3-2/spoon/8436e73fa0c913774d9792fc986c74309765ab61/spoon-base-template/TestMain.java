import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.List;

public class TestMain {
    public static void main(String[] args) {
        String sourceFile = "/workspace/billy/billy-portugal/src-generated/main/java/com/premiumminds/billy/portugal/webservices/series/SeriesWS.java";
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceFile);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        
        List<CtCompilationUnit> compilationUnits = model.getElements(new TypeFilter<>(CtCompilationUnit.class));
        System.out.println("Found " + compilationUnits.size() + " compilation units");
        
        for (CtCompilationUnit cu : compilationUnits) {
            System.out.println("Processing: " + cu.getFile().getName());
            List<CtImport> imports = cu.getImports();
            System.out.println("  Has " + imports.size() + " imports");
            for (CtImport imp : imports) {
                System.out.println("    Import: " + imp.getReference().toString());
            }
        }
    }
}
