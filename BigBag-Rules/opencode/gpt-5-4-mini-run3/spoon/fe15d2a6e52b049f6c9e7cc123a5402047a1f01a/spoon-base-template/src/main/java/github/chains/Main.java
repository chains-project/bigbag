package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtTypeReference;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String RT = "org.jvnet.jaxb2_commons.lang.";

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        Path input = Paths.get(args[0]).toAbsolutePath().normalize();
        Path output = args.length == 2
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : input.resolveSibling(input.getFileName() + "-fixed");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPreserveLineNumbers(true);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.addProcessor(new JaxbBasicsMigrationProcessor());
        launcher.run();
    }

    public static class JaxbBasicsMigrationProcessor extends AbstractProcessor<CtElement> {
        @Override
        public void process(CtElement element) {
            if (element instanceof CtTypeReference<?> typeRef) {
                rewriteTypeReference(typeRef);
            }
            if (element instanceof CtInvocation<?> invocation) {
                rewriteInvocation(invocation);
            }
        }

        private void rewriteTypeReference(CtTypeReference<?> ref) {
            String qn = ref.getQualifiedName();
            CtPackageReference pack = ref.getPackage();
            if (pack == null || !RT.substring(0, RT.length() - 1).equals(pack.getQualifiedName())) {
                return;
            }
            if (qn.equals(RT + "ToString")) {
                ref.setSimpleName("ToString2");
            } else if (qn.equals(RT + "CopyTo")) {
                ref.setSimpleName("CopyTo2");
            } else if (qn.equals(RT + "CopyStrategy")) {
                ref.setSimpleName("CopyStrategy2");
            } else if (qn.equals(RT + "EqualsStrategy")) {
                ref.setSimpleName("EqualsStrategy2");
            } else if (qn.equals(RT + "HashCodeStrategy")) {
                ref.setSimpleName("HashCodeStrategy2");
            } else if (qn.equals(RT + "MergeStrategy")) {
                ref.setSimpleName("MergeStrategy2");
            }
        }

        private void rewriteInvocation(CtInvocation<?> invocation) {
            CtTypeReference<?> targetType = null;
            if (invocation.getExecutable().getDeclaringType() != null) {
                targetType = invocation.getExecutable().getDeclaringType();
            } else if (invocation.getTarget() != null) {
                targetType = invocation.getTarget().getType();
            }
            if (targetType == null) return;

            String qn = targetType.getQualifiedName();
            String method = invocation.getExecutable().getSimpleName();
            int argCount = invocation.getArguments().size();

            if (qn.equals(RT + "ToStringStrategy") || qn.equals(RT + "ToStringStrategy2")) {
                if (method.equals("appendField") && argCount == 5) {
                    addPresenceArgument(invocation, invocation.getArguments().get(4));
                }
                return;
            }

            if (qn.equals(RT + "CopyStrategy") || qn.equals(RT + "CopyStrategy2")) {
                if (method.equals("copy") && argCount == 2) {
                    addPresenceArgument(invocation, invocation.getArguments().get(1));
                } else if (method.equals("shouldBeCopiedAndSet") && argCount == 2) {
                    // already new API shape
                }
                return;
            }

            if (qn.equals(RT + "EqualsStrategy") || qn.equals(RT + "EqualsStrategy2")) {
                if (method.equals("equals") && argCount == 4) {
                    List<CtExpression<?>> args = new ArrayList<>(invocation.getArguments());
                    args.add(presenceExpression(args.get(2)));
                    args.add(presenceExpression(args.get(3)));
                    invocation.setArguments(args);
                }
                return;
            }

            if (qn.equals(RT + "HashCodeStrategy") || qn.equals(RT + "HashCodeStrategy2")) {
                if (method.equals("hashCode") && argCount == 3) {
                    addPresenceArgument(invocation, invocation.getArguments().get(2));
                }
                return;
            }

            if (qn.equals(RT + "MergeStrategy") || qn.equals(RT + "MergeStrategy2")) {
                if (method.equals("merge") && argCount == 4) {
                    List<CtExpression<?>> args = new ArrayList<>(invocation.getArguments());
                    args.add(presenceExpression(args.get(2)));
                    args.add(presenceExpression(args.get(3)));
                    invocation.setArguments(args);
                }
            }
        }

        private void addPresenceArgument(CtInvocation<?> invocation, CtExpression<?> valueExpression) {
            List<CtExpression<?>> args = new ArrayList<>(invocation.getArguments());
            args.add(presenceExpression(valueExpression));
            invocation.setArguments(args);
        }

        private CtExpression<Boolean> presenceExpression(CtExpression<?> expression) {
            CtTypeReference<?> type = expression == null ? null : expression.getType();
            if (type != null && type.isPrimitive()) {
                return getFactory().Code().createCodeSnippetExpression("true");
            }
            if (expression == null) {
                return getFactory().Code().createCodeSnippetExpression("true");
            }
            return getFactory().Code().createCodeSnippetExpression(expression.prettyprint() + " != null");
        }
    }
}
