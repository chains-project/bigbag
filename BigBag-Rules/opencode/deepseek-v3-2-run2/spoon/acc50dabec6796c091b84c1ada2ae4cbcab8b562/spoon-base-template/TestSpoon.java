import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.visitor.ProcessingVisitor;

public class TestSpoon {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        System.out.println("Testing Spoon API...");
        
        // Test creating a reference
        CtTypeReference<?> ref = launcher.getFactory().createReference("java.lang.String");
        System.out.println("Created reference: " + ref.getQualifiedName());
    }
}
