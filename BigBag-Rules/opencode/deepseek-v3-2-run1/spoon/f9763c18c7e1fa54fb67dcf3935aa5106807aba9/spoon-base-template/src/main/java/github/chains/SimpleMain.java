package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.code.CtExpression;
import java.util.List;

public class SimpleMain {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(args[0]);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        
        CtModel model = launcher.buildModel();
        
        // Find all new Class(...) expressions
        List<CtNewClass<?>> newClasses = model.getElements(new TypeFilter<CtNewClass<?>>(CtNewClass.class));
        System.out.println("Found " + newClasses.size() + " new Class() expressions");
        
        for (CtNewClass<?> newClass : newClasses) {
            String typeName = newClass.getType().getQualifiedName();
            System.out.println("New class: " + typeName + " at " + newClass.getPosition());
            
            if ("com.gargoylesoftware.htmlunit.ScriptResult".equals(typeName)) {
                System.out.println("FOUND ScriptResult!");
                List<CtExpression<?>> argsList = newClass.getArguments();
                System.out.println("Has " + argsList.size() + " arguments");
            }
        }
    }
}