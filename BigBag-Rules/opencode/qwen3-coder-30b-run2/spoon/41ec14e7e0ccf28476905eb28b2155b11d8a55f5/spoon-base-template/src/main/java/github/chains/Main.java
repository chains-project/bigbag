package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Filter;

public class Main {
    public static void main(String[] args) {
        // Parse the source files
        Launcher launcher = new Launcher();
        launcher.setArgs(new String[]{
            "--output-type", "nooutput",
            "--source-classpath", "/workspace/wicket-crudifier/target/classes:/workspace/wicket-crudifier/src/main/java",
            "--target-classpath", "/workspace/wicket-crudifier/target/classes"
        });
        
        // Add source folders to parse
        launcher.addInputResource("/workspace/wicket-crudifier/src/main/java");
        
        // Build the model
        CtModel model = launcher.buildModel();
        Factory factory = launcher.getFactory();

        // Pattern 1: Remove all javax.validation imports
        model.getElements(new Filter<CtImport>() {
            @Override
            public boolean matches(CtImport element) {
                return element.getReference().toString().startsWith("javax.validation");
            }
        }).forEach(CtImport::delete);

        // Pattern 2: Remove all org.apache.commons.beanutils imports
        model.getElements(new Filter<CtImport>() {
            @Override
            public boolean matches(CtImport element) {
                return element.getReference().toString().startsWith("org.apache.commons.beanutils");
            }
        }).forEach(CtImport::delete);

        // Pattern 3: Find and replace PropertyUtils.getPropertyDescriptor calls
        model.getElements(new Filter<CtInvocation>() {
            @Override
            public boolean matches(CtInvocation element) {
                if (element.getExecutable() == null) return false;
                return element.getExecutable().getSimpleName().equals("getPropertyDescriptor") &&
                       element.getExecutable().getDeclaringType() != null &&
                       element.getExecutable().getDeclaringType().getQualifiedName().equals("org.apache.commons.beanutils.PropertyUtils");
            }
        }).forEach(invocation -> {
            // Replace with placeholder comment to indicate removal
            try {
                invocation.replace(factory.Code().createCodeSnippetExpression("/* Removed PropertyUtils.getPropertyDescriptor usage */"));
            } catch (Exception e) {
                invocation.delete();
            }
        });
        
        // Pattern 4: Remove HibernateValidatorProperty.validatorFactory usage
        model.getElements(new Filter<CtFieldAccess>() {
            @Override
            public boolean matches(CtFieldAccess element) {
                if (element.getVariable() == null) return false;
                return element.getVariable().getSimpleName().equals("validatorFactory") &&
                       element.getVariable().getDeclaringType() != null &&
                       element.getVariable().getDeclaringType().getQualifiedName().equals("com.premiumminds.webapp.wicket.validators.HibernateValidatorProperty");
            }
        }).forEach(fieldAccess -> {
            try {
                fieldAccess.replace(factory.Code().createCodeSnippetExpression("/* Removed validatorFactory access */"));
            } catch (Exception e) {
                fieldAccess.delete();
            }
        });

        // Write back the modified code
        launcher.setSourceOutputDirectory("/workspace/wicket-crudifier/src/main/java");
        launcher.process();
        
        System.out.println("Transformation applied successfully.");
    }
}