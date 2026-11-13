package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import java.util.List;

public class TestProcessor {
    public static void main(String[] args) {
        // Simple test to understand AST structure
        String testCode = "import java.util.List;\n" +
                         "public class Test {\n" +
                         "    private List<ManagedObject> list1;\n" +
                         "    private List<ManagedObject<?>> list2;\n" +
                         "}\n";
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        
        launcher.addInputResource(testCode);
        launcher.addProcessor(new DebugProcessor());
        launcher.setSourceOutputDirectory("test-output");
        launcher.run();
    }
    
    static class DebugProcessor extends AbstractProcessor<CtField<?>> {
        @Override
        public void process(CtField<?> field) {
            System.out.println("Field: " + field.getSimpleName());
            System.out.println("Type: " + field.getType());
            System.out.println("Type class: " + field.getType().getClass());
            
            if (field.getType() instanceof spoon.reflect.reference.CtActualTypeContainer) {
                spoon.reflect.reference.CtActualTypeContainer container = 
                    (spoon.reflect.reference.CtActualTypeContainer) field.getType();
                List<CtTypeReference<?>> args = container.getActualTypeArguments();
                System.out.println("Type args: " + args);
                if (args != null) {
                    for (int i = 0; i < args.size(); i++) {
                        System.out.println("  Arg " + i + ": " + args.get(i));
                        System.out.println("  Arg " + i + " class: " + args.get(i).getClass());
                        System.out.println("  Arg " + i + " qualified name: " + args.get(i).getQualifiedName());
                    }
                }
            }
            System.out.println("---");
        }
    }
}