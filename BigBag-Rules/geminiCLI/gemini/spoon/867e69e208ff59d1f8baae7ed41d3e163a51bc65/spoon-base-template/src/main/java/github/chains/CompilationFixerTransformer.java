package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.*;
import spoon.reflect.code.*;
import spoon.reflect.reference.*;
import java.io.IOException;
import java.util.Set;

public class CompilationFixerTransformer extends AbstractProcessor<CtElement> {
    @Override
    public void process(CtElement element) {
        // Fix 1: MappedFileTBinaryProtocol
        if (element instanceof CtClass) {
            CtClass<?> clazz = (CtClass<?>) element;
            if ("MappedFileTBinaryProtocol".equals(clazz.getSimpleName())) {
                 boolean exists = clazz.getMethods().stream().anyMatch(m -> "getMinSerializedSize".equals(m.getSimpleName()));
                 if (!exists) {
                     CtMethod<Integer> method = getFactory().Core().createMethod();
                     method.setSimpleName("getMinSerializedSize");
                     method.setType(getFactory().Type().integerPrimitiveType());
                     method.addModifier(ModifierKind.PUBLIC);
                     method.addAnnotation(getFactory().createAnnotation(getFactory().createReference("java.lang.Override")));
                     
                     CtParameter<Byte> param = getFactory().Core().createParameter();
                     param.setSimpleName("type");
                     param.setType(getFactory().Type().bytePrimitiveType());
                     method.addParameter(param);

                     CtBlock<Integer> body = getFactory().Core().createBlock();
                     CtReturn<Integer> ret = getFactory().Core().createReturn();
                     ret.setReturnedExpression(getFactory().Code().createLiteral(0));
                     body.addStatement(ret);
                     method.setBody(body);
                     
                     clazz.addMethod(method);
                 }
            }
        }
        
        // Fix 2: KafkaWriter SERIALIZER
        if (element instanceof CtField) {
            CtField<?> field = (CtField<?>) element;
            if ("SERIALIZER".equals(field.getSimpleName()) && "KafkaWriter".equals(field.getDeclaringType().getSimpleName())) {
                String newCode = "ThreadLocal.withInitial(() -> { try { return new org.apache.thrift.TSerializer(); } catch (org.apache.thrift.TException e) { throw new RuntimeException(e); } })";
                CtExpression<?> newExpr = getFactory().Code().createCodeSnippetExpression(newCode);
                field.setDefaultExpression((CtExpression) newExpr);
            }
        }
        
        // Fix 3: ThriftReader constructor
        if (element instanceof CtConstructor) {
            CtConstructor<?> constr = (CtConstructor<?>) element;
            if ("ThriftReader".equals(constr.getDeclaringType().getSimpleName())) {
                 for (CtStatement stmt : constr.getBody().getStatements()) {
                     if (stmt instanceof CtAssignment) {
                         CtAssignment<?,?> assign = (CtAssignment<?,?>) stmt;
                         if (assign.getAssigned() instanceof CtFieldWrite) {
                             CtFieldWrite<?> fw = (CtFieldWrite<?>) assign.getAssigned();
                             if ("framedTransport".equals(fw.getVariable().getSimpleName())) {
                                 
                                 CtTry tryBlock = getFactory().Core().createTry();
                                 tryBlock.setBody(getFactory().Core().createBlock());
                                 tryBlock.getBody().addStatement(stmt.clone());
                                 
                                 CtCatch catchBlock = getFactory().Core().createCatch();
                                 CtCatchVariable<Exception> catchVar = getFactory().Core().createCatchVariable();
                                 catchVar.setSimpleName("e");
                                 catchVar.setType(getFactory().Type().createReference("org.apache.thrift.transport.TTransportException"));
                                 catchBlock.setParameter(catchVar);
                                 
                                 CtBlock<?> catchBody = getFactory().Core().createBlock();
                                 CtThrow throwStmt = getFactory().Core().createThrow();
                                 CtConstructorCall<IOException> newEx = getFactory().Core().createConstructorCall();
                                 newEx.setType(getFactory().Type().createReference("java.io.IOException"));
                                 newEx.addArgument(getFactory().Code().createVariableRead(catchVar.getReference(), false));
                                 throwStmt.setThrownExpression(newEx);
                                 
                                 catchBody.addStatement(throwStmt);
                                 catchBlock.setBody(catchBody);
                                 
                                 tryBlock.addCatcher(catchBlock);
                                 
                                 stmt.replace(tryBlock);
                             }
                         }
                     }
                 }
            }
        }

        // Fix 4: SimpleThriftLogger
        if (element instanceof CtClass) {
            CtClass<?> clazz = (CtClass<?>) element;
            if ("SimpleThriftLogger".equals(clazz.getSimpleName())) {
                // Fix inner class constructor
                for (CtTypeMember member : clazz.getTypeMembers()) {
                    if (member instanceof CtClass && "ByteOffsetTFramedTransport".equals(((CtClass) member).getSimpleName())) {
                        CtClass<?> inner = (CtClass<?>) member;
                        for (CtConstructor<?> c : inner.getConstructors()) {
                            // Add throws
                            Set<CtTypeReference<? extends Throwable>> thrownTypes = c.getThrownTypes();
                            boolean hasIt = false;
                            for(CtTypeReference<? extends Throwable> t : thrownTypes) {
                                if ("org.apache.thrift.transport.TTransportException".equals(t.getQualifiedName())) {
                                    hasIt = true; 
                                    break;
                                }
                            }
                            if (!hasIt) {
                                c.addThrownType(getFactory().Type().createReference("org.apache.thrift.transport.TTransportException"));
                            }
                        }
                    }
                }
                
                // Fix rotate method
                for (CtMethod<?> method : clazz.getMethods()) {
                    if ("rotate".equals(method.getSimpleName())) {
                        for (CtStatement stmt : method.getBody().getStatements()) {
                            if (stmt instanceof CtAssignment) {
                                CtAssignment assign = (CtAssignment) stmt;
                                if (assign.getAssigned() instanceof CtFieldWrite && "transport".equals(((CtFieldWrite)assign.getAssigned()).getVariable().getSimpleName())) {
                                     // Wrap in try-catch
                                     CtTry tryBlock = getFactory().Core().createTry();
                                     tryBlock.setBody(getFactory().Core().createBlock());
                                     tryBlock.getBody().addStatement(stmt.clone());
                                     
                                     CtCatch catchBlock = getFactory().Core().createCatch();
                                     CtCatchVariable<Exception> catchVar = getFactory().Core().createCatchVariable();
                                     catchVar.setSimpleName("e");
                                     catchVar.setType(getFactory().Type().createReference("org.apache.thrift.transport.TTransportException"));
                                     catchBlock.setParameter(catchVar);
                                     
                                     CtBlock<?> catchBody = getFactory().Core().createBlock();
                                     CtThrow throwStmt = getFactory().Core().createThrow();
                                     CtConstructorCall<IOException> newEx = getFactory().Core().createConstructorCall();
                                     newEx.setType(getFactory().Type().createReference("java.io.IOException"));
                                     newEx.addArgument(getFactory().Code().createVariableRead(catchVar.getReference(), false));
                                     throwStmt.setThrownExpression(newEx);
                                     
                                     catchBody.addStatement(throwStmt);
                                     catchBlock.setBody(catchBody);
                                     
                                     tryBlock.addCatcher(catchBlock);
                                     
                                     stmt.replace(tryBlock);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}