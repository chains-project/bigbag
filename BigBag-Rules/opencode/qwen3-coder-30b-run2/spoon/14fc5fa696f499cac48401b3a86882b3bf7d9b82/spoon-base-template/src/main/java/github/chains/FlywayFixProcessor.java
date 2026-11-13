package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Spoon processor to detect Flyway constructor calls that need to be updated for Flyway 9.x compatibility
 */
public class FlywayFixProcessor extends AbstractProcessor<CtElement> {

    @Override
    public void process(CtElement element) {
        // Find all constructor calls to Flyway class without arguments
        List<CtConstructorCall> flywayCalls = element.getElements(new TypeFilter<>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall constructorCall) {
                return constructorCall.getType() != null && 
                       constructorCall.getType().getSimpleName().equals("Flyway") &&
                       constructorCall.getArguments().isEmpty(); // Empty constructor call
            }
        });

        for (CtConstructorCall call : flywayCalls) {
            // Report the problematic call
            System.out.println("Found problematic Flyway constructor call:");
            System.out.println("  File: " + call.getPosition().getFile().getName());
            System.out.println("  Line: " + call.getPosition().getLine());
            System.out.println("  Code: " + call.toString());
            System.out.println("  Fix: Change to 'Flyway.configure().dataSource(...).locations(...).load()' pattern");
            System.out.println();
        }
    }
}