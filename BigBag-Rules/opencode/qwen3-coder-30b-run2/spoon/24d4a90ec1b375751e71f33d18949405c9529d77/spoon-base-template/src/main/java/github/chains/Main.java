package github.chains;

import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.visitor.CtScanner;
import spoon.reflect.declaration.CtElement;
import java.util.HashMap;
import java.util.Map;

/**
 * Generic Spoon transformation rule to fix breaking changes in jcabi-aspects
 * where Tv constants (FIVE, HUNDRED, THOUSAND, etc.) were removed.
 * This rule replaces references to these constants with literal numeric values.
 * 
 * Usage:
 * Factory factory = ...; // Your Spoon factory
 * CtElement root = ...; // Root element to transform
 * Main.replaceTvConstants(factory, root);
 */
public class Main {

    /**
     * Maps Tv constant names to their corresponding integer values.
     */
    private static final Map<String, Integer> TV_CONSTANTS = new HashMap<>();

    static {
        TV_CONSTANTS.put("THREE", 3);
        TV_CONSTANTS.put("FOUR", 4);
        TV_CONSTANTS.put("FIVE", 5);
        TV_CONSTANTS.put("SIX", 6);
        TV_CONSTANTS.put("SEVEN", 7);
        TV_CONSTANTS.put("EIGHT", 8);
        TV_CONSTANTS.put("NINE", 9);
        TV_CONSTANTS.put("TEN", 10);
        TV_CONSTANTS.put("FIFTEEN", 15);
        TV_CONSTANTS.put("TWENTY", 20);
        TV_CONSTANTS.put("THIRTY", 30);
        TV_CONSTANTS.put("FORTY", 40);
        TV_CONSTANTS.put("FIFTY", 50);
        TV_CONSTANTS.put("SIXTY", 60);
        TV_CONSTANTS.put("SEVENTY", 70);
        TV_CONSTANTS.put("EIGHTY", 80);
        TV_CONSTANTS.put("NINETY", 90);
        TV_CONSTANTS.put("HUNDRED", 100);
        TV_CONSTANTS.put("THOUSAND", 1000);
        TV_CONSTANTS.put("MILLION", 1000000);
        TV_CONSTANTS.put("BILLION", 1000000000);
    }

    /**
     * Applies the transformation to replace Tv constants with literal values.
     * This is the core transformation logic that can be used in any Spoon-based tool.
     * 
     * @param factory The Spoon factory for creating new AST nodes
     * @param element The root element to scan for Tv constant references
     */
    public static void replaceTvConstants(Factory factory, CtElement element) {
        // Create a scanner to find all field accesses
        CtScanner scanner = new CtScanner() {
            @Override
            public void scan(CtElement element) {
                if (element instanceof CtFieldAccess) {
                    CtFieldAccess<?> fieldAccess = (CtFieldAccess<?>) element;
                    CtFieldReference<?> fieldRef = fieldAccess.getVariable();
                    
                    // Check if this is a reference to a Tv constant
                    if (fieldRef != null && 
                        fieldRef.getDeclaringType() != null &&
                        "com.jcabi.aspects.Tv".equals(fieldRef.getDeclaringType().getQualifiedName()) &&
                        TV_CONSTANTS.containsKey(fieldRef.getSimpleName())) {
                        
                        // Replace with literal integer value
                        Integer value = TV_CONSTANTS.get(fieldRef.getSimpleName());
                        if (value != null) {
                            CtLiteral<Integer> literal = factory.Code().createLiteral(value);
                            fieldAccess.replace(literal);
                        }
                    }
                }
                super.scan(element);
            }
        };
        
        scanner.scan(element);
    }
}