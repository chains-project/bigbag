package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation rule to fix breaking changes in htmlunit ScriptResult usage
 * This transformation removes imports and replaces ScriptResult usage with direct object handling
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source-directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "/transformed");

        // Process the code
        CtModel model = launcher.buildModel();

        // Find all imports of ScriptResult and remove them
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        for (CtImport imp : imports) {
            if (imp.getReference() != null && imp.getReference().toString().contains("com.gargoylesoftware.htmlunit.ScriptResult")) {
                // Remove the import
                imp.delete();
            }
        }

        // Find all new ScriptResult instantiations and replace them
        List<CtNewClass> scriptResultInstantiations = model.getElements(new TypeFilter<>(CtNewClass.class) {
            @Override
            public boolean matches(CtNewClass element) {
                return element.getType().toString().contains("com.gargoylesoftware.htmlunit.ScriptResult");
            }
        });

        // Replace new ScriptResult() calls with direct result handling
        for (CtNewClass newClass : scriptResultInstantiations) {
            // Replace the whole expression with first argument (the result)
            if (!newClass.getArguments().isEmpty()) {
                // Direct replacement without type casting
                newClass.replace(newClass.getArguments().get(0));
            }
        }

        // Find all method calls to ScriptResult.getJavaScriptResult() and replace them
        List<CtInvocation> getJavaScriptResultCalls = model.getElements(new TypeFilter<>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation element) {
                return element.getExecutable() != null && 
                       element.getExecutable().getSimpleName().equals("getJavaScriptResult");
            }
        });

        // Replace calls to getJavaScriptResult() with direct access to the result
        for (CtInvocation invocation : getJavaScriptResultCalls) {
            // Replace the call with the target expression (the ScriptResult object)
            CtInvocation target = invocation.getTarget();
            if (target != null) {
                invocation.replace(target);
            }
        }

        // Save the modified code
        launcher.process();
        System.out.println("Transformation completed successfully!");
    }
}