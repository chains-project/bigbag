import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

public class TestSpoonAPI {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("Test.java");
        CtModel model = launcher.buildModel();
        
        System.out.println("Testing Spoon API...");
    }
}
