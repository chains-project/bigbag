package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtBlock;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;

public class FixThriftException {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        // Set the input sources to the singer project
        launcher.addInputResource("/workspace/singer/singer-commons/src/main/java/com/pinterest/singer/loggingaudit/client/AuditEventKafkaSender.java");
        
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);

        launcher.buildModel();
        CtModel model = launcher.getModel();

        for (CtType<?> type : model.getAllTypes()) {
            if (type.getQualifiedName().equals("com.pinterest.singer.loggingaudit.client.AuditEventKafkaSender")) {
                CtClass<?> clazz = (CtClass<?>) type;
                
                // 1. Remove initializer from field
                CtField<?> serializerField = clazz.getField("serializer");
                if (serializerField != null && serializerField.getDefaultExpression() != null) {
                    serializerField.setDefaultExpression(null);
                }

                // 2. Add initialization to constructor
                if (!clazz.getConstructors().isEmpty()) {
                    CtConstructor<?> constructor = clazz.getConstructors().iterator().next();
                    Factory factory = launcher.getFactory();
                    
                    String tryCatchBlock = 
                        "try { " +
                        "  this.serializer = new org.apache.thrift.TSerializer(); " +
                        "} catch (org.apache.thrift.transport.TTransportException e) { " +
                        "  throw new RuntimeException(e); " +
                        "}";
                    
                    CtBlock<?> body = constructor.getBody();
                    if (body != null) {
                         body.insertBegin(factory.Code().createCodeSnippetStatement(tryCatchBlock));
                    }
                }
            }
        }

        // Save changes
        launcher.setSourceOutputDirectory("/workspace/singer/singer-commons/src/main/java");
        launcher.prettyprint();
    }
}
