package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        // Get the source directory from command line args or default to current directory
        String sourceDir = args.length > 0 ? args[0] : "/workspace/docker-adapter";
        
        // Create a Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(sourceDir + "/generated");
        launcher.buildModel();

        // Find all constructor calls to StringContains with 2 arguments (boolean, String)
        List<CtConstructorCall<?>> stringContainsCalls = launcher.getModel().getElements(
            new TypeFilter<>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall<?> element) {
                    return element.getType().getSimpleName().equals("StringContains") && 
                           element.getArguments().size() == 2;
                }
            }
        );
        
        // Find all constructor calls to StringStartsWith with 2 arguments (boolean, String)
        List<CtConstructorCall<?>> stringStartsWithCalls = launcher.getModel().getElements(
            new TypeFilter<>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall<?> element) {
                    return element.getType().getSimpleName().equals("StringStartsWith") && 
                           element.getArguments().size() == 2;
                }
            }
        );

        // Fix StringContains constructor calls - remove the first boolean argument
        for (CtConstructorCall<?> call : stringContainsCalls) {
            if (call.getArguments().size() == 2) {
                call.getArguments().remove(0);
            }
        }

        // Fix StringStartsWith constructor calls - remove the first boolean argument
        for (CtConstructorCall<?> call : stringStartsWithCalls) {
            if (call.getArguments().size() == 2) {
                call.getArguments().remove(0);
            }
        }

        // Generate the fixed code
        launcher.setSourceOutputDirectory(sourceDir + "/generated");
        launcher.prettyprint();
        
        System.out.println("Transformation complete. Fixed " + stringContainsCalls.size() + 
                          " StringContains and " + stringStartsWithCalls.size() + " StringStartsWith calls");
    }
}
            }
        );
        
        // Find all constructor calls to StringStartsWith with 2 arguments (boolean, String)
        List<CtConstructorCall<?>> stringStartsWithCalls = launcher.getModel().getElements(
            new TypeFilter<>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall<?> element) {
                    return element.getType().getSimpleName().equals("StringStartsWith") && 
                           element.getArguments().size() == 2;
                }
            }
        );

        // Fix StringContains constructor calls - remove the first boolean argument
        for (CtConstructorCall<?> call : stringContainsCalls) {
            if (call.getArguments().size() == 2) {
                call.getArguments().remove(0);
            }
        }

        // Fix StringStartsWith constructor calls - remove the first boolean argument
        for (CtConstructorCall<?> call : stringStartsWithCalls) {
            if (call.getArguments().size() == 2) {
                call.getArguments().remove(0);
            }
        }

        // Generate the fixed code
        launcher.setSourceOutputDirectory(sourceDir + "/generated");
        launcher.prettyprint();
        
        System.out.println("Transformation complete. Fixed " + stringContainsCalls.size() + 
                          " StringContains and " + stringStartsWithCalls.size() + " StringStartsWith calls");
    }
}
            }
        );
        
        List<CtConstructorCall<?>> stringStartsWithCalls = launcher.getModel().getElements(
            new TypeFilter<>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall<?> element) {
                    return element.getType().getSimpleName().equals("StringStartsWith") && 
                           element.getArguments().size() == 2;
                }
            }
        );

        // Fix StringContains constructor calls - remove the first boolean argument
        for (CtConstructorCall<?> call : stringContainsCalls) {
            if (call.getArguments().size() == 2) {
                call.getArguments().remove(0);
            }
        }

        // Fix StringStartsWith constructor calls - remove the first boolean argument
        for (CtConstructorCall<?> call : stringStartsWithCalls) {
            if (call.getArguments().size() == 2) {
                call.getArguments().remove(0);
            }
        }

        // Generate the fixed code
        launcher.setSourceOutputDirectory(sourceDir + "/generated");
        launcher.prettyprint();
    }
}