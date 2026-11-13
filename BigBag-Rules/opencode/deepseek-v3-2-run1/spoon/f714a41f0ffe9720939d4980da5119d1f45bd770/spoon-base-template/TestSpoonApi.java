import spoon.Launcher;
import spoon.reflect.factory.Factory;

public class TestSpoonApi {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        Factory factory = launcher.getFactory();
        System.out.println("Factory class: " + factory.getClass());
        System.out.println("Type().createReference(String) exists: " + 
            factory.Type().getClass().getMethods());
    }
}
