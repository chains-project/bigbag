import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import java.util.List;

public class TestTransformation extends AbstractProcessor<CtCompilationUnit> {
    private static final String OLD_PACKAGE_PREFIX = "com.mysql.jdbc";
    private static final String NEW_PACKAGE_PREFIX = "com.mysql.cj.jdbc";
    
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);
        launcher.addProcessor(new TestTransformation());
        launcher.run();
    }
    
    @Override
    public void process(CtCompilationUnit cu) {
        System.out.println("Processing: " + cu.getFile().getName());
        List<CtImport> imports = cu.getImports();
        System.out.println("Found " + imports.size() + " imports");
        
        for (CtImport ctImport : imports) {
            String importString = ctImport.getReference().toString();
            System.out.println("  Import: " + importString);
            
            if (importString.startsWith(OLD_PACKAGE_PREFIX)) {
                System.out.println("  *** Would transform: " + importString);
                String newImport = importString.replace(OLD_PACKAGE_PREFIX, NEW_PACKAGE_PREFIX);
                CtTypeReference<?> newRef = getFactory().createReference(newImport);
                ctImport.setReference(newRef);
                System.out.println("  *** Transformed to: " + newImport);
            }
        }
    }
}