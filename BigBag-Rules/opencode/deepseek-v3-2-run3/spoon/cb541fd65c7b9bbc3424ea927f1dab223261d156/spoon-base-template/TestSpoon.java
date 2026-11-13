import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;

public class TestSpoon {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        
        CtScanner scanner = new CtScanner() {
            @Override
            public void visitCtImport(CtImport ctImport) {
                super.visitCtImport(ctImport);
                System.out.println("Found import: " + ctImport.getReference());
            }
            
            @Override
            public <T> void visitCtInvocation(CtInvocation<T> invocation) {
                super.visitCtInvocation(invocation);
                System.out.println("Found invocation: " + invocation);
            }
        };
        
        System.out.println("Test created");
    }
}
