import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

public class TestTransformation {
    public static void main(String[] args) throws Exception {
        Launcher launcher = new Launcher();
        launcher.addInputResource(args[0]);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        
        List<CtConstructorCall<?>> yamlConstructorCalls = model
            .getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall<?> constructorCall) {
                    if (constructorCall.getType() == null) return false;
                    String typeName = constructorCall.getType().getQualifiedName();
                    return "org.yaml.snakeyaml.Yaml".equals(typeName) && 
                           constructorCall.getArguments().size() == 4;
                }
            });
        
        System.out.println("Found: " + yamlConstructorCalls.size());
        for (CtConstructorCall<?> call : yamlConstructorCalls) {
            System.out.println("Call: " + call);
            System.out.println("Args: " + call.getArguments().size());
        }
    }
}
