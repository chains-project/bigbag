package github.chains;

import spoon.Launcher;
import spoon.SpoonAPI;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.Set;
import java.util.HashSet;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <project-directory>");
            System.exit(1);
        }
        
        String projectDir = args[0];
        System.out.println("Analyzing project: " + projectDir);
        
        try {
            // Create Spoon launcher
            SpoonAPI spoon = new Launcher();
            
            // Load the project
            spoon.addInputResource(projectDir);
            
            // Build the model
            CtModel model = spoon.buildModel();
            
            // Check if project uses Xpp3Dom
            boolean usesXpp3Dom = checkUsesXpp3Dom(model);
            
            if (usesXpp3Dom) {
                System.out.println("\n================================================");
                System.out.println("BREAKING CHANGE DETECTED: Xpp3Dom usage found");
                System.out.println("================================================");
                System.out.println("\nProblem: Xpp3Dom was removed from plexus-utils 4.0.0");
                System.out.println("Old API: org.codehaus.plexus.util.xml.Xpp3Dom (in plexus-utils < 4.0.0)");
                System.out.println("New API: org.codehaus.plexus.util.xml.Xpp3Dom (in plexus-xml 4.1.0+)");
                System.out.println("\nSolution: Add dependency on plexus-xml");
                System.out.println("\nAdd to your pom.xml:");
                System.out.println("<dependency>");
                System.out.println("    <groupId>org.codehaus.plexus</groupId>");
                System.out.println("    <artifactId>plexus-xml</artifactId>");
                System.out.println("    <version>4.1.0</version>");
                System.out.println("</dependency>");
                System.out.println("\nNote: The import statement remains the same.");
                System.out.println("================================================");
            } else {
                System.out.println("No Xpp3Dom usage detected.");
            }
            
        } catch (Exception e) {
            System.err.println("Error analyzing project: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static boolean checkUsesXpp3Dom(CtModel model) {
        // Check for imports of Xpp3Dom
        Set<CtImport> imports = new HashSet<>();
        for (CtImport imp : model.getElements(new TypeFilter<CtImport>(CtImport.class))) {
            String importStr = imp.toString();
            if (importStr.contains("org.codehaus.plexus.util.xml.Xpp3Dom")) {
                imports.add(imp);
            }
        }
        
        if (!imports.isEmpty()) {
            return true;
        }
        
        // Check for type references to Xpp3Dom
        Set<CtTypeReference<?>> typeRefs = new HashSet<>();
        for (CtTypeReference<?> ref : model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class))) {
            if ("org.codehaus.plexus.util.xml.Xpp3Dom".equals(ref.getQualifiedName())) {
                typeRefs.add(ref);
            }
        }
        
        if (!typeRefs.isEmpty()) {
            return true;
        }
        
        // Check for instanceof checks, casts, etc.
        Set<CtExpression<?>> expressions = new HashSet<>();
        for (CtExpression<?> expr : model.getElements(new TypeFilter<CtExpression<?>>(CtExpression.class))) {
            String exprStr = expr.toString();
            // Check for instanceof Xpp3Dom
            if (exprStr.contains("instanceof Xpp3Dom") || 
                exprStr.contains("instanceof org.codehaus.plexus.util.xml.Xpp3Dom")) {
                expressions.add(expr);
                continue;
            }
            
            // Check for casts to Xpp3Dom
            if (exprStr.contains("(Xpp3Dom)") || 
                exprStr.contains("(org.codehaus.plexus.util.xml.Xpp3Dom)")) {
                expressions.add(expr);
            }
        }
        
        return !expressions.isEmpty();
    }
}