package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;

import java.util.List;

public class Main extends AbstractProcessor<CtMethod> {

    @Override
    public void process(CtMethod method) {
        // Check if this is a method from the DateAndTime class that takes Date parameters
        CtType<?> declaringType = method.getDeclaringType();
        if (declaringType != null && declaringType.getQualifiedName().equals("net.datafaker.DateAndTime")) {
            // Check if method signature has Date parameters
            List<CtParameter> parameters = method.getParameters();
            for (CtParameter param : parameters) {
                if (param.getType().getQualifiedName().equals("java.util.Date")) {
                    // Change parameter type from java.util.Date to java.sql.Timestamp
                    CtTypeReference<?> newType = getFactory().Type().createReference("java.sql.Timestamp");
                    param.setType(newType);
                }
            }
            
            // Check if return type is Date and change it to Timestamp
            if (method.getType().getQualifiedName().equals("java.util.Date")) {
                CtTypeReference<?> newReturnType = getFactory().Type().createReference("java.sql.Timestamp");
                method.setType(newReturnType);
            }
        }
    }

    @Override
    public boolean isToBeProcessed(CtMethod candidate) {
        // Only process methods from DateAndTime class
        if (candidate.getDeclaringType() == null) {
            return false;
        }
        String declaringType = candidate.getDeclaringType().getQualifiedName();
        if (!declaringType.equals("net.datafaker.DateAndTime")) {
            return false;
        }
        
        // Check if any parameter is java.util.Date
        List<CtParameter> parameters = candidate.getParameters();
        for (CtParameter param : parameters) {
            if (param.getType().getQualifiedName().equals("java.util.Date")) {
                return true;
            }
        }
        
        // Check if return type is java.util.Date
        return candidate.getType().getQualifiedName().equals("java.util.Date");
    }
}