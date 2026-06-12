import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

public class SimpleSpoonTest {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(".");
        CtModel model = launcher.buildModel();
        
        List<CtCompilationUnit> cus = model.getElements(new TypeFilter<>(CtCompilationUnit.class));
        for (CtCompilationUnit cu : cus) {
            System.out.println("CU: " + cu.getFile().getName());
            for (CtImport imp : cu.getImports()) {
                System.out.println("  Import: " + imp);
            }
        }
    }
}
