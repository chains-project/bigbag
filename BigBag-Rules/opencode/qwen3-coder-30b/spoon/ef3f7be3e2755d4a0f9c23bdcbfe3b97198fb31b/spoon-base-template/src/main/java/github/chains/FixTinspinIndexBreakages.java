package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class FixTinspinIndexBreakages extends AbstractProcessor<CtInvocation> {
    
    @Override
    public boolean isToBeProcessed(CtInvocation candidate) {
        // Process all invocation expressions
        return true;
    }
    
    @Override
    public void process(CtInvocation invocation) {
        Factory factory = getFactory();
        
        // Fix 1: Replace query1NN with query1nn method calls
        if (invocation.getExecutable() != null && "query1NN".equals(invocation.getExecutable().getSimpleName())) {
            // Change method name from query1NN to query1nn
            invocation.getExecutable().setSimpleName("query1nn");
        }
    }
}