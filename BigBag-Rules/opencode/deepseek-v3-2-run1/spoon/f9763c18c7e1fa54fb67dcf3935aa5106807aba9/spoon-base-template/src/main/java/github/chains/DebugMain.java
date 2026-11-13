package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.declaration.CtElement;
import java.util.List;

public class DebugMain {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(args[0]);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        
        try {
            CtModel model = launcher.buildModel();
            System.out.println("Model built successfully");
            
            // Get all elements
            List<CtElement> allElements = model.getElements(new TypeFilter<CtElement>(CtElement.class));
            System.out.println("Total elements: " + allElements.size());
            
            // Print first few
            for (int i = 0; i < Math.min(20, allElements.size()); i++) {
                System.out.println(i + ": " + allElements.get(i).getClass().getSimpleName() + " - " + allElements.get(i));
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}