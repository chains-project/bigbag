package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) {
        // Create a Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Set the source directory to process
        launcher.addInputResource("/workspace/singer");
        
        // Process the code
        CtModel model = launcher.buildModel();
        
        // Find the specific class that needs fixing
        model.getElements(new TypeFilter<>(CtClass.class)).forEach(clazz -> {
            if (clazz.getQualifiedName().equals("com.pinterest.singer.client.logback.AppenderUtils$LogMessageEncoder")) {
                System.out.println("Found LogMessageEncoder class to fix");
                
                // Check if it implements footerBytes and headerBytes methods
                boolean hasFooterBytes = false;
                boolean hasHeaderBytes = false;
                
                for (CtMethod<?> method : clazz.getMethods()) {
                    if (method.getSimpleName().equals("footerBytes")) {
                        hasFooterBytes = true;
                    } else if (method.getSimpleName().equals("headerBytes")) {
                        hasHeaderBytes = true;
                    }
                }
                
                // If missing one or both methods, add them
                if (!hasFooterBytes) {
                    System.out.println("Adding footerBytes method");
                    String footerMethod = "public byte[] footerBytes() { return new byte[0]; }";
                    CtMethod<?> newMethod = launcher.getFactory().Code().createMethod(footerMethod);
                    clazz.addMethod(newMethod);
                }
                
                if (!hasHeaderBytes) {
                    System.out.println("Adding headerBytes method");
                    String headerMethod = "public byte[] headerBytes() { return new byte[0]; }";
                    CtMethod<?> newMethod = launcher.getFactory().Code().createMethod(headerMethod);
                    clazz.addMethod(newMethod);
                }
            }
        });
        
        // Write the result back to files
        launcher.setSourceOutputDirectory("/workspace/singer");
        launcher.process();
    }
}