import spoon.Launcher;
import spoon.reflect.factory.Factory;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

public class TestSpoon {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        Factory factory = launcher.getFactory();
        
        // Test creating a method invocation
        CtTypeReference<?> typeRef = factory.Type().createReference("java.lang.String");
        CtExecutableReference<?> execRef = factory.Executable().createReference(typeRef, "toString");
        
        System.out.println("Created executable reference: " + execRef);
    }
}
