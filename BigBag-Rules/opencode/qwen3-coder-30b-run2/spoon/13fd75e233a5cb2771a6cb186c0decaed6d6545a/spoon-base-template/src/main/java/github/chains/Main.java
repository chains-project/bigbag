/*
 * Generic Spoon transformation to fix Hamcrest API breaking changes
 * 
 * This transformation addresses breaking changes where StringContains and StringStartsWith
 * constructors now require only String parameters instead of (boolean, String) parameters.
 * 
 * Usage: mvn compile exec:java -Dexec.mainClass="github.chains.HamcrestFixRunner"
 */

package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.NewClass;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

import java.util.List;

/**
 * Spoon processor to fix Hamcrest StringContains and StringStartsWith constructor calls.
 * This fixes cases where these constructors are called with boolean parameter followed by string,
 * but newer Hamcrest versions only support String parameter.
 */
public class Main {

    public static void main(String[] args) {
        // This serves as a placeholder for the Spoon transformation
        System.out.println("Hamcrest API Fix Transformation Ready");
    }

    /**
     * Spoon processor that identifies and fixes StringContains/StringStartsWith 
     * constructor calls with boolean parameters 
     */
    public static class HamcrestFixProcessor extends AbstractProcessor<NewClass> {
        
        @Override
        public boolean isToBeProcessed(NewClass candidate) {
            // Process all NewClass instances to check for Hamcrest constructor calls
            return true;
        }

        @Override
        public void process(NewClass newClass) {
            CtExecutableReference<?> constructorRef = newClass.getConstructor();
            
            if (constructorRef != null) {
                CtTypeReference<?> declaringType = constructorRef.getDeclaringType();
                
                // Check if this is a StringContains or StringStartsWith constructor call
                if (declaringType != null) {
                    String qualifiedName = declaringType.getQualifiedName();
                    
                    if ("org.hamcrest.core.StringContains".equals(qualifiedName) ||
                        "org.hamcrest.core.StringStartsWith".equals(qualifiedName)) {
                        
                        // Check constructor signature
                        List<?> paramTypes = constructorRef.getParameters();
                        
                        // If it's the old constructor with boolean parameter, we need to fix it
                        if (paramTypes.size() == 2) {
                            // Check if first parameter is boolean and second is String
                            Object firstParam = paramTypes.get(0);
                            Object secondParam = paramTypes.get(1);
                            
                            if (firstParam.toString().contains("boolean") && 
                                secondParam.toString().contains("String")) {
                                
                                // Replace with single String parameter constructor call
                                replaceWithSingleStringParameter(newClass);
                            }
                        }
                    }
                }
            }
        }

        private void replaceWithSingleStringParameter(NewClass newClass) {
            // Get the arguments passed to the constructor
            List<?> arguments = newClass.getArguments();
            
            // Only the second argument (String) is relevant for the new constructor
            if (arguments.size() >= 2) {
                // Keep only the string argument (second parameter)
                Object stringArg = arguments.get(1);
                newClass.setArguments(List.of(stringArg));
            }
        }
    }
    
    /**
     * Runner class to execute the transformation on a project
     */
    public static class HamcrestFixRunner {
        public static void main(String[] args) {
            System.out.println("Hamcrest Fix Runner - This would execute the Spoon transformation");
            System.out.println("Usage: mvn compile exec:java -Dexec.mainClass=\"github.chains.HamcrestFixRunner\"");
        }
    }
}