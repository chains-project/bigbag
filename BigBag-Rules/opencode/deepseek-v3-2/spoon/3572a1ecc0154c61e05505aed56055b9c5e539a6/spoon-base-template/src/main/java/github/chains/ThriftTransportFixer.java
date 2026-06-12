package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.visitor.CtScanner;
import java.util.HashMap;
import java.util.Map;

/**
 * Generic processor for fixing Thrift 0.16.0 package relocation.
 * Transforms:
 * - org.apache.thrift.transport.TFastFramedTransport -> org.apache.thrift.transport.layered.TFastFramedTransport
 * - org.apache.thrift.transport.TFramedTransport -> org.apache.thrift.transport.layered.TFramedTransport
 * 
 * This is a generic, reusable transformation that can be applied to any project
 * affected by the Thrift 0.16.0 breaking change.
 */
public class ThriftTransportFixer extends AbstractProcessor<CtType<?>> {
    
    private final Map<String, String> packageRelocations;
    
    public ThriftTransportFixer() {
        this.packageRelocations = new HashMap<>();
        packageRelocations.put("org.apache.thrift.transport.TFastFramedTransport", 
                              "org.apache.thrift.transport.layered.TFastFramedTransport");
        packageRelocations.put("org.apache.thrift.transport.TFramedTransport", 
                              "org.apache.thrift.transport.layered.TFramedTransport");
    }
    
    @Override
    public void process(CtType<?> type) {
        // Transform imports
        for (CtImport imp : type.getPosition().getCompilationUnit().getImports()) {
            String importString = imp.getReference().toString();
            for (Map.Entry<String, String> entry : packageRelocations.entrySet()) {
                if (importString.equals(entry.getKey())) {
                    System.out.println("Transforming import: " + importString + " -> " + entry.getValue());
                    CtTypeReference<?> newRef = getFactory().createReference(entry.getValue());
                    imp.setReference(newRef);
                }
            }
        }
        
        // Use a scanner to find and replace type references and constructor calls
        type.accept(new CtScanner() {
            @Override
            public <T> void visitCtConstructorCall(CtConstructorCall<T> ctConstructorCall) {
                super.visitCtConstructorCall(ctConstructorCall);
                
                CtTypeReference<?> constructedType = ctConstructorCall.getType();
                String typeName = constructedType.getQualifiedName();
                
                for (Map.Entry<String, String> entry : packageRelocations.entrySet()) {
                    if (typeName.equals(entry.getKey())) {
                        System.out.println("Transforming constructor call: " + typeName + " -> " + entry.getValue());
                        CtTypeReference<?> newRef = getFactory().createReference(entry.getValue());
                        ctConstructorCall.setType(newRef);
                    }
                }
            }
            
            @Override
            public <T> void visitCtTypeReference(CtTypeReference<T> reference) {
                super.visitCtTypeReference(reference);
                
                String typeName = reference.getQualifiedName();
                for (Map.Entry<String, String> entry : packageRelocations.entrySet()) {
                    if (typeName.equals(entry.getKey())) {
                        System.out.println("Transforming type reference: " + typeName + " -> " + entry.getValue());
                        CtTypeReference<?> newRef = getFactory().createReference(entry.getValue());
                        reference.replace(newRef);
                    }
                }
            }
        });
    }
}