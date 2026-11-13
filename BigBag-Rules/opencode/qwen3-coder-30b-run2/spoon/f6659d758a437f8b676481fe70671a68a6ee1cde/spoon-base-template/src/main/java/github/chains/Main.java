package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * Generic Spoon transformation to fix SnakeYAML 2.1 breaking change:
 * Removal of org.yaml.snakeyaml.inspector.TrustedTagInspector
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp <classpath> github.chains.Main <source_directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "_fixed");
        launcher.getEnvironment().setAutoImports(true);

        CtModel model = launcher.buildModel();

        // Remove import statements for TrustedTagInspector
        model.getElements(new TypeFilter<>(CtImport.class))
                .stream()
                .filter(importStmt -> importStmt.toString().contains("TrustedTagInspector"))
                .forEach(importStmt -> {
                    System.out.println("Removing import: " + importStmt.toString());
                    importStmt.delete();
                });

        // Remove usage of TrustedTagInspector constructor calls
        model.getElements(new TypeFilter<>(CtNewClass.class))
                .stream()
                .filter(newClass -> newClass.toString().contains("TrustedTagInspector"))
                .forEach(newClass -> {
                    System.out.println("Removing TrustedTagInspector construction: " + newClass.toString());
                    // Replace with null to maintain compilation
                    newClass.replace(newClass.getFactory().createLiteral(null));
                });

        // Print summary
        System.out.println("Transformation completed. Check " + sourceDirectory + "_fixed directory.");
        
        launcher.process();
    }
}

        String sourceDirectory = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "_fixed");
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(8);

        CtModel model = launcher.buildModel();

        int importCount = 0;
        int usageCount = 0;
        
        // Remove import statements for TrustedTagInspector
        List<CtImport> importsToRemove = model.getElements(new TypeFilter<>(CtImport.class))
                .stream()
                .filter(importStmt -> {
                    String importStr = importStmt.toString();
                    return importStr.contains("TrustedTagInspector") && 
                           importStr.contains("org.yaml.snakeyaml.inspector");
                })
                .toList();
                
        for (CtImport importStmt : importsToRemove) {
            System.out.println("Removing import: " + importStmt.toString());
            importStmt.delete();
            importCount++;
        }

        // Remove usage of TrustedTagInspector constructor calls
        List<CtNewClass> newClassesToRemove = model.getElements(new TypeFilter<>(CtNewClass.class))
                .stream()
                .filter(newClass -> newClass.toString().contains("TrustedTagInspector"))
                .toList();
                
        for (CtNewClass newClass : newClassesToRemove) {
            System.out.println("Removing TrustedTagInspector construction: " + newClass.toString());
            // Replace with null to maintain compilation
            newClass.replace(newClass.getFactory().createLiteral(null));
            usageCount++;
        }

        // Also look for direct references in code to replace them properly
        List<CtExpression<?>> expressionsToRemove = model.getElements(new TypeFilter<>(CtExpression.class))
                .stream()
                .filter(expr -> expr.toString().contains("new TrustedTagInspector()"))
                .toList();
                
        for (CtExpression<?> expr : expressionsToRemove) {
            System.out.println("Replacing TrustedTagInspector construction: " + expr.toString());
            expr.replace(expr.getFactory().createLiteral(null));
            usageCount++;
        }

        // Print summary
        System.out.println("Transformation completed.");
        System.out.println("Removed " + importCount + " import statements");
        System.out.println("Removed " + usageCount + " TrustedTagInspector usages");
        System.out.println("Fixed files saved to " + sourceDirectory + "_fixed directory.");
        
        launcher.process();
    }
}