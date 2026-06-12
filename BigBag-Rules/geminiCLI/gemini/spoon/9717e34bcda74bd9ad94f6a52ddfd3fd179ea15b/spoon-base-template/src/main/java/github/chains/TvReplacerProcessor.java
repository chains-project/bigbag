package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TvReplacerProcessor extends AbstractProcessor<CtElement> {
    private static final Map<String, Integer> MAPPING = new HashMap<>();
    static {
        MAPPING.put("THREE", 3);
        MAPPING.put("FOUR", 4);
        MAPPING.put("FIVE", 5);
        MAPPING.put("SIX", 6);
        MAPPING.put("SEVEN", 7);
        MAPPING.put("EIGHT", 8);
        MAPPING.put("NINE", 9);
        MAPPING.put("TEN", 10);
        MAPPING.put("FIFTEEN", 15);
        MAPPING.put("TWENTY", 20);
        MAPPING.put("THIRTY", 30);
        MAPPING.put("FORTY", 40);
        MAPPING.put("FIFTY", 50);
        MAPPING.put("SIXTY", 60);
        MAPPING.put("SEVENTY", 70);
        MAPPING.put("EIGHTY", 80);
        MAPPING.put("NINETY", 90);
        MAPPING.put("HUNDRED", 100);
        MAPPING.put("THOUSAND", 1000);
        MAPPING.put("MILLION", 1000000);
        MAPPING.put("BILLION", 1000000000);
    }

    @Override
    public void process(CtElement element) {
        if (element instanceof CtFieldRead) {
            processFieldRead((CtFieldRead<?>) element);
        } else if (element instanceof CtCompilationUnit) {
            processCompilationUnit((CtCompilationUnit) element);
        } else if (element instanceof CtTypeAccess) {
            processTypeAccess((CtTypeAccess<?>) element);
        }
    }

    private void processFieldRead(CtFieldRead<?> fieldRead) {
        if (!fieldRead.getVariable().isStatic()) {
            return;
        }
        
        CtTypeReference<?> declaringType = fieldRead.getVariable().getDeclaringType();
        if (declaringType != null && "com.jcabi.aspects.Tv".equals(declaringType.getQualifiedName())) {
             String fieldName = fieldRead.getVariable().getSimpleName();
             if (MAPPING.containsKey(fieldName)) {
                 CtExpression<Integer> newLiteral = getFactory().Code().createLiteral(MAPPING.get(fieldName));
                 fieldRead.replace(newLiteral);
             }
        }
    }

    private void processCompilationUnit(CtCompilationUnit unit) {
        List<CtImport> toRemove = new ArrayList<>();
        List<CtImport> toAdd = new ArrayList<>();

        for (CtImport imp : unit.getImports()) {
            String importStr = imp.toString();
            // Check for Tv import
            if (importStr.contains("com.jcabi.aspects.Tv")) {
                toRemove.add(imp);
            }
            // Check for bad JsonValue import
            if (importStr.contains("JsonValue.ValueType")) {
                toRemove.add(imp);
                // Create correct import: javax.json.JsonValue.ValueType
                CtTypeReference<?> typeRef = getFactory().Type().createReference("javax.json.JsonValue.ValueType");
                toAdd.add(getFactory().Type().createImport(typeRef));
            }
        }

        for (CtImport imp : toRemove) {
            unit.getImports().remove(imp);
        }
        for (CtImport imp : toAdd) {
            unit.getImports().add(imp);
        }
    }

    private void processTypeAccess(CtTypeAccess<?> typeAccess) {
        CtTypeReference<?> typeRef = typeAccess.getAccessedType();
        if (typeRef != null && "ValueType".equals(typeRef.getSimpleName())) {
             // If usage is ValueType, force it to be javax.json.JsonValue.ValueType
             CtTypeReference<?> newRef = getFactory().Type().createReference("javax.json.JsonValue.ValueType");
             typeAccess.setAccessedType((CtTypeReference)newRef);
        }
    }
}
