import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.Launcher;

public class TestSpoonAPI {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        Factory factory = launcher.getFactory();
        CtCompilationUnit cu = factory.createCompilationUnit();
        CtTypeReference<?> ref = factory.Type().createReference("java.lang.String");
        // Check available methods
        System.out.println("Methods in CtCompilationUnit:");
        for (java.lang.reflect.Method m : cu.getClass().getMethods()) {
            if (m.getName().contains("Import")) {
                System.out.println("  " + m);
            }
        }
    }
}
