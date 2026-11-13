import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

public class TestSpoon {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(args[0]);
        CtModel model = launcher.buildModel();
        
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> element) {
                return element.getExecutable() != null && 
                       "parseEnchantment".equals(element.getExecutable().getSimpleName());
            }
        });
        
        System.out.println("Found: " + invocations.size());
        for (CtInvocation<?> inv : invocations) {
            System.out.println("At: " + inv.getPosition() + " - " + inv);
        }
    }
}
