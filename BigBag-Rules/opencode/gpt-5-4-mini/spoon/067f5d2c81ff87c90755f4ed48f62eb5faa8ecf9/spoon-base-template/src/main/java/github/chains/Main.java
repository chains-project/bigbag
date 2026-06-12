package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    private static final String SERIALIZER_FQN = "org.apache.thrift.TSerializer";
    private static final String DESERIALIZER_FQN = "org.apache.thrift.TDeserializer";
    private static final String T_TRANSPORT_EXCEPTION_FQN = "org.apache.thrift.transport.TTransportException";
    private static final String ILLEGAL_STATE_EXCEPTION_FQN = "java.lang.IllegalStateException";
    private static final String SERIALIZER_HELPER = "__spoonCreateTSerializer";
    private static final String DESERIALIZER_HELPER = "__spoonCreateTDeserializer";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> <output-source-dir>");
        }

        Path input = Paths.get(args[0]);
        Path output = Paths.get(args[1]);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setCopyResources(false);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.buildModel();
        launcher.addProcessor(new ThriftConstructorFixProcessor());
        launcher.process();
        launcher.prettyprint();
    }

    private static final class ThriftConstructorFixProcessor extends AbstractProcessor<CtConstructorCall<?>> {
        private final Set<String> processedHelpers = new HashSet<>();

        @Override
        public void process(CtConstructorCall<?> call) {
            CtTypeReference<?> constructedType = call.getType();
            if (constructedType == null) {
                return;
            }

            String typeName = constructedType.getQualifiedName();
            if (!SERIALIZER_FQN.equals(typeName) && !DESERIALIZER_FQN.equals(typeName)) {
                return;
            }

            CtType<?> owner = call.getParent(CtType.class);
            if (owner == null) {
                return;
            }

            String helperName = SERIALIZER_FQN.equals(typeName) ? SERIALIZER_HELPER : DESERIALIZER_HELPER;
            ensureHelper(owner, helperName, typeName, call.getArguments());

            String replacement = helperName + "(" + joinArguments(call.getArguments()) + ")";
            call.replace(getFactory().Code().createCodeSnippetExpression(replacement));
        }

        private void ensureHelper(CtType<?> owner, String helperName, String targetTypeName, List<CtExpression<?>> args) {
            if (!(owner instanceof CtClass)) {
                return;
            }

            String key = owner.getQualifiedName() + "#" + helperName + "(" + signature(args) + ")";
            if (processedHelpers.contains(key)) {
                return;
            }
            boolean alreadyPresent = owner.getMethodsByName(helperName).stream()
                    .anyMatch(method -> method.getParameters().size() == args.size());
            if (alreadyPresent) {
                processedHelpers.add(key);
                return;
            }

            Factory factory = getFactory();
            List<CtParameter<?>> parameters = new ArrayList<>();
            for (int i = 0; i < args.size(); i++) {
                CtTypeReference<?> parameterType = args.get(i) != null && args.get(i).getType() != null
                        ? args.get(i).getType().clone()
                        : factory.Type().OBJECT.clone();
                CtParameter<?> parameter = factory.Core().createParameter();
                parameter.setSimpleName("arg" + i);
                parameter.setType(parameterType);
                parameters.add(parameter);
            }

            StringBuilder invocation = new StringBuilder("new ").append(targetTypeName).append('(');
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) {
                    invocation.append(", ");
                }
                invocation.append("arg").append(i);
            }
            invocation.append(')');

            String body = "try { return " + invocation + "; } catch (" + T_TRANSPORT_EXCEPTION_FQN + " e) { throw new RuntimeException(\"Failed to create " + targetTypeName + "\", e); }";
            CtStatement bodyStatement = factory.Code().createCodeSnippetStatement(body);
            CtBlock<?> block = factory.Code().createCtBlock(bodyStatement);
            Set<ModifierKind> modifiers = new java.util.LinkedHashSet<>();
            modifiers.add(ModifierKind.PRIVATE);
            modifiers.add(ModifierKind.STATIC);
            Set<CtTypeReference<? extends Throwable>> thrown = new java.util.LinkedHashSet<>();

            CtClass<?> classOwner = (CtClass<?>) owner;
            factory.createMethod(classOwner, modifiers, factory.Type().createReference(targetTypeName), helperName, parameters, thrown, block);
            processedHelpers.add(key);
        }

        private String joinArguments(List<CtExpression<?>> args) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(args.get(i));
            }
            return builder.toString();
        }

        private String signature(List<CtExpression<?>> args) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) {
                    builder.append(",");
                }
                CtExpression<?> arg = args.get(i);
                builder.append(arg == null || arg.getType() == null ? "java.lang.Object" : arg.getType().getQualifiedName());
            }
            return builder.toString();
        }
    }
}
