package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.visitor.Filter;

import java.io.File;

public class Main {
    public static void main(String[] args) {
        // If no argument provided, use the default project path
        String projectPath = args.length > 0 ? args[0] : "/workspace/billy";
        
        System.out.println("Applying Jakarta JWS API transformation to: " + projectPath);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.addInputResource(projectPath);
        launcher.buildModel();
        
        CtModel model = launcher.getModel();
        
        // Transform all imports from javax.jws to jakarta.jws
        model.getElements(new Filter<CtImport>() {
            @Override
            public boolean matches(CtImport element) {
                if (element.getReference() != null) {
                    String importRef = element.getReference().toString();
                    return importRef.startsWith("javax.jws");
                }
                return false;
            }
        }).forEach(importElement -> {
            String importRef = importElement.getReference().toString();
            if (importRef.startsWith("javax.jws")) {
                String newImport = importRef.replace("javax.jws", "jakarta.jws");
                importElement.setReference(launcher.getFactory().createImport(
                    launcher.getFactory().Type().createReference(newImport)));
            }
        });
        
        // Write back the modified model
        launcher.setSourceOutputDirectory(new File(projectPath + "/transformed"));
        launcher.prettyprint();
        
        System.out.println("Transformation completed successfully!");
    }
}