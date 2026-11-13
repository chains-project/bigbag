import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.Launcher;

public class TestSpoon {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        Factory factory = launcher.getFactory();
        // Try to create type reference
        CtTypeReference<?> ref = factory.Type().createReference("java.lang.String");
        System.out.println("Created: " + ref);
    }
}
