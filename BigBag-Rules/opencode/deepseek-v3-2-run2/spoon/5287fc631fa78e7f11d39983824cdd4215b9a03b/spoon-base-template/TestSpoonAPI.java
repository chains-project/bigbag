import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import java.util.List;

public class TestSpoonAPI {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        System.out.println("Testing Spoon API...");
        
        // Check available methods
        launcher.getFactory(); // This should work
        
        // Create a simple test
        CtTypeReference<?> ref = launcher.getFactory().createReference("java.lang.String");
        System.out.println("Created reference: " + ref.getQualifiedName());
    }
}
