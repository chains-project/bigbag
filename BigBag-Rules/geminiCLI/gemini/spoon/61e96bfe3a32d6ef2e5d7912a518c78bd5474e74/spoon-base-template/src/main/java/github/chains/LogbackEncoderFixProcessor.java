package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtTypeReference;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.List;

public class LogbackEncoderFixProcessor extends AbstractProcessor<CtClass<?>> {
    @Override
    public boolean isToBeProcessed(CtClass<?> candidate) {
        return "com.pinterest.singer.client.logback.AppenderUtils".equals(candidate.getQualifiedName());
    }

    @Override
    public void process(CtClass<?> ctClass) {
        System.out.println("Processing " + ctClass.getQualifiedName());
        CtType<?> nested = ctClass.getNestedType("LogMessageEncoder");
        if (nested instanceof CtClass) {
             processEncoder((CtClass<?>) nested);
        } else {
             System.out.println("LogMessageEncoder not found or not a class");
        }
    }

    private void processEncoder(CtClass<?> ctClass) {
        System.out.println("Processing inner class " + ctClass.getQualifiedName());
        Factory factory = getFactory();
        
        // Remove old methods
        Set<CtMethod<?>> toRemove = new HashSet<>();
        for (CtMethod<?> method : ctClass.getMethods()) {
            if (method.getSimpleName().equals("init") || 
                method.getSimpleName().equals("doEncode") || 
                method.getSimpleName().equals("close")) {
                toRemove.add(method);
            }
        }
        for (CtMethod<?> method : toRemove) {
            ctClass.removeMethod(method);
        }
        
        // Remove fields
        Set<String> fieldsToRemove = new HashSet<>(Arrays.asList("os", "framedTransport", "protocol"));
        for (String fieldName : fieldsToRemove) {
            CtField<?> field = ctClass.getField(fieldName);
            if (field != null) {
                ctClass.removeField(field);
            }
        }
        
        // Add headerBytes method
        CtMethod<byte[]> headerBytes = factory.createMethod(
            ctClass,
            new HashSet<>(Arrays.asList(ModifierKind.PUBLIC)),
            factory.createArrayReference(factory.Type().bytePrimitiveType()),
            "headerBytes",
            Arrays.asList(),
            new HashSet<>()
        );
        headerBytes.setBody(factory.createCodeSnippetStatement("return null"));
        
        // Add footerBytes method
        CtMethod<byte[]> footerBytes = factory.createMethod(
            ctClass,
            new HashSet<>(Arrays.asList(ModifierKind.PUBLIC)),
            factory.createArrayReference(factory.Type().bytePrimitiveType()),
            "footerBytes",
            Arrays.asList(),
            new HashSet<>()
        );
        footerBytes.setBody(factory.createCodeSnippetStatement("return null"));

        // Add encode method
        CtTypeReference<?> byteArrayType = factory.createArrayReference(factory.Type().bytePrimitiveType());
        CtMethod<byte[]> encode = factory.createMethod(
            ctClass,
            new HashSet<>(Arrays.asList(ModifierKind.PUBLIC)),
            (CtTypeReference<byte[]>) byteArrayType,
            "encode",
            Arrays.asList(factory.createParameter(null, factory.Type().createReference("com.pinterest.singer.thrift.LogMessage"), "logMessage")),
            new HashSet<>()
        );
        
        String body = 
            "try {\n" +
            "    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();\n" +
            "    org.apache.thrift.transport.TIOStreamTransport ioTransport = new org.apache.thrift.transport.TIOStreamTransport(baos);\n" +
            "    org.apache.thrift.transport.TTransport transport = new org.apache.thrift.transport.TFastFramedTransport(ioTransport, 10);\n" +
            "    org.apache.thrift.protocol.TProtocol proto = new org.apache.thrift.protocol.TBinaryProtocol(transport);\n" +
            "    logMessage.write(proto);\n" +
            "    transport.flush();\n" +
            "    return baos.toByteArray();\n" +
            "} catch (Exception e) {\n" +
            "    throw new RuntimeException(e);\n" +
            "}";
        
        encode.setBody(factory.createCodeSnippetStatement(body));
    }
}
