package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.factory.Factory;

public class Main {

    private static final String PROFILE_PROPERTY_FQN = "org.spongepowered.api.profile.property.ProfileProperty";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> <output-source-dir>");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);
        launcher.addProcessor(new SpongeApi8ProfileProcessor());
        launcher.run();
    }

    private static final class SpongeApi8ProfileProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public void process(CtInvocation<?> invocation) {
            renameSpongeServerAccessor(invocation);
            renameServerPlayerAccessor(invocation);
            renameGameProfileManagerAccessor(invocation);
            rewriteProfilePropertyFactory(invocation);
            rewritePropertyMapClear(invocation);
            rewritePropertyMapPut(invocation);
        }

        private void renameSpongeServerAccessor(CtInvocation<?> invocation) {
            if ("getServer".equals(invocation.getExecutable().getSimpleName())) {
                invocation.getExecutable().setSimpleName("server");
            }
        }

        private void renameServerPlayerAccessor(CtInvocation<?> invocation) {
            if ("getPlayer".equals(invocation.getExecutable().getSimpleName()) && invocation.getArguments().size() == 1) {
                invocation.getExecutable().setSimpleName("player");
            }
        }

        private void renameGameProfileManagerAccessor(CtInvocation<?> invocation) {
            if ("getGameProfileManager".equals(invocation.getExecutable().getSimpleName())) {
                invocation.getExecutable().setSimpleName("gameProfileManager");
            }
        }

        private void rewriteProfilePropertyFactory(CtInvocation<?> invocation) {
            if (!"createProfileProperty".equals(invocation.getExecutable().getSimpleName())) {
                return;
            }

            String replacement = PROFILE_PROPERTY_FQN + ".of(" + joinArguments(invocation) + ")";
            CtExpression<?> expr = invocation.getFactory().Code().createCodeSnippetExpression(replacement);
            invocation.replace(expr);
        }

        private void rewritePropertyMapClear(CtInvocation<?> invocation) {
            if (!"clear".equals(invocation.getExecutable().getSimpleName())) {
                return;
            }

            CtInvocation<?> propertyMapInvocation = asInvocation(invocation.getTarget());
            if (propertyMapInvocation == null || !"getPropertyMap".equals(propertyMapInvocation.getExecutable().getSimpleName())) {
                return;
            }

            CtExpression<?> profileExpression = propertyMapInvocation.getTarget();
            if (profileExpression == null) {
                return;
            }

            String profile = profileExpression.toString();
            replaceWithSnippet(invocation, profile + " = " + profile + ".withoutProperties();");
        }

        private void rewritePropertyMapPut(CtInvocation<?> invocation) {
            if (!"put".equals(invocation.getExecutable().getSimpleName()) || invocation.getArguments().size() != 2) {
                return;
            }

            CtInvocation<?> propertyMapInvocation = asInvocation(invocation.getTarget());
            if (propertyMapInvocation == null || !"getPropertyMap".equals(propertyMapInvocation.getExecutable().getSimpleName())) {
                return;
            }

            CtExpression<?> profileExpression = propertyMapInvocation.getTarget();
            if (profileExpression == null) {
                return;
            }

            String profile = profileExpression.toString();
            String property = toProfilePropertyExpression(invocation.getArguments().get(1));
            replaceWithSnippet(invocation, profile + " = " + profile + ".withProperty(" + property + ");");
        }

        private String toProfilePropertyExpression(CtExpression<?> expression) {
            CtInvocation<?> invocation = asInvocation(expression);
            if (invocation == null || !"createProfileProperty".equals(invocation.getExecutable().getSimpleName())) {
                return expression.toString();
            }

            return PROFILE_PROPERTY_FQN + ".of(" + joinArguments(invocation) + ")";
        }

        private String joinArguments(CtInvocation<?> invocation) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < invocation.getArguments().size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(invocation.getArguments().get(i).toString());
            }
            return builder.toString();
        }

        private void replaceWithSnippet(CtElement element, String statement) {
            Factory factory = element.getFactory();
            CtStatement replacement = factory.Code().createCodeSnippetStatement(statement);
            element.replace(replacement);
        }

        @SuppressWarnings("unchecked")
        private CtInvocation<?> asInvocation(CtExpression<?> expression) {
            if (expression instanceof CtInvocation) {
                return (CtInvocation<?>) expression;
            }

            return null;
        }
    }
}
