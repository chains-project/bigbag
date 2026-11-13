package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.processing.AbstractProcessor;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic Spoon transformation that migrates Java projects affected by the
 * Jetty 8/9 to Jetty 11 breaking change.
 *
 * <p>Every rule is parameterized by fully-qualified type names of the DEPENDENCY
 * (Jetty / Servlet API). Client class names (e.g. {@code JettyStubHttpServer}) are
 * never mentioned in the rule, so the same transformation can be run on any Maven
 * project by simply passing a different input source directory.</p>
 *
 * <p>Breaking changes handled:</p>
 * <ol>
 *   <li>Servlet API namespace rename: {@code javax.servlet.*} to {@code jakarta.servlet.*}.</li>
 *   <li>{@code org.eclipse.jetty.server.nio.SelectChannelConnector} removed;
 *       replaced by {@code org.eclipse.jetty.server.ServerConnector}.</li>
 *   <li>{@code Connector.setPort}/{@code Connector.getLocalPort} removed from the interface,
 *       now live only on {@code ServerConnector}; receiver fields / locals typed as
 *       {@code Connector} are widened to {@code ServerConnector}.</li>
 *   <li>Jetty 11 constructor {@code ServerConnector(Server)} requires a {@code Server}
 *       argument; {@code new ServerConnector()} (previously no-arg) gets the in-scope
 *       {@code Server} passed in.</li>
 *   <li>{@code Server.setSendServerVersion}/{@code Server.setSendDateHeader} moved to
 *       {@code HttpConfiguration}; receivers are rewritten to a locally-declared
 *       {@code HttpConfiguration}.</li>
 * </ol>
 */
public class Main {

    // ------------------ Parameterization (FQNs of the DEPENDENCY only) ------------------

    static final String OLD_SERVLET_PACKAGE = "javax.servlet.";
    static final String NEW_SERVLET_PACKAGE = "jakarta.servlet.";

    static final String OLD_CONNECTOR_CLASS = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    static final String NEW_CONNECTOR_CLASS = "org.eclipse.jetty.server.ServerConnector";

    static final String CONNECTOR_INTERFACE    = "org.eclipse.jetty.server.Connector";
    static final String JETTY_SERVER_CLASS     = "org.eclipse.jetty.server.Server";
    static final String HTTP_CONFIGURATION_CLASS = "org.eclipse.jetty.server.HttpConfiguration";

    static final List<String> SERVER_SETTERS_MOVED_TO_HTTP_CONFIG =
            List.of("setSendServerVersion", "setSendDateHeader");

    static final List<String> CONNECTOR_METHODS_REQUIRING_SERVER_CONNECTOR =
            List.of("setPort", "getLocalPort");

    // ---------------------------------------------------------------------

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp <cp> github.chains.Main <source-dir> [output-dir]");
            System.exit(1);
        }
        String sourceDir = args[0];
        String outputDir = args.length > 1 ? args[1] : sourceDir;

        System.out.println("[spoon-rule] input  = " + sourceDir);
        System.out.println("[spoon-rule] output = " + outputDir);

        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        // Any additional source directories listed after [output-dir] are used for
        // type resolution only (so Spoon doesn't mis-bind fully-qualified names).
        // They do NOT affect the transformation logic; the rule stays generic.
        for (int i = 2; i < args.length; i++) {
            System.out.println("[spoon-rule] classpath-src = " + args[i]);
            launcher.addInputResource(args[i]);
        }
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.setSourceOutputDirectory(outputDir);

        CtModel model = launcher.buildModel();

        model.processWith(new PackageRenameProcessor(OLD_SERVLET_PACKAGE, NEW_SERVLET_PACKAGE));
        model.processWith(new ClassRenameProcessor(OLD_CONNECTOR_CLASS, NEW_CONNECTOR_CLASS));
        cleanupObsoleteImports(model, List.of(
                OLD_SERVLET_PACKAGE,
                "org.eclipse.jetty.server.nio."));
        model.processWith(new ConnectorReceiverWideningProcessor(
                CONNECTOR_INTERFACE, NEW_CONNECTOR_CLASS,
                CONNECTOR_METHODS_REQUIRING_SERVER_CONNECTOR));
        model.processWith(new ServerConnectorConstructorArgumentProcessor(
                NEW_CONNECTOR_CLASS, JETTY_SERVER_CLASS));
        model.processWith(new ServerSetterToHttpConfigurationProcessor(
                JETTY_SERVER_CLASS, HTTP_CONFIGURATION_CLASS,
                SERVER_SETTERS_MOVED_TO_HTTP_CONFIG));

        launcher.prettyprint();
        System.out.println("[spoon-rule] done.");
    }

    // ==================================================================
    // 1. Package rename for the dependency's servlet namespace.
    // ==================================================================
    public static class PackageRenameProcessor extends AbstractProcessor<CtTypeReference<?>> {
        private final String oldPrefix;
        private final String newPrefix;

        public PackageRenameProcessor(String oldPrefix, String newPrefix) {
            this.oldPrefix = oldPrefix;
            this.newPrefix = newPrefix;
        }

        @Override
        public void process(CtTypeReference<?> ref) {
            String qName = ref.getQualifiedName();
            if (qName == null || !qName.startsWith(oldPrefix)) return;
            String newFqn = newPrefix + qName.substring(oldPrefix.length());
            int dot = newFqn.lastIndexOf('.');
            ref.setPackage(getFactory().Package().createReference(newFqn.substring(0, dot)));
            ref.setSimpleName(newFqn.substring(dot + 1));
        }
    }

    // ==================================================================
    // 2. Rename one specific dependency class (FQN -> FQN).
    // ==================================================================
    public static class ClassRenameProcessor extends AbstractProcessor<CtTypeReference<?>> {
        private final String oldFqn;
        private final String newFqn;

        public ClassRenameProcessor(String oldFqn, String newFqn) {
            this.oldFqn = oldFqn;
            this.newFqn = newFqn;
        }

        @Override
        public void process(CtTypeReference<?> ref) {
            if (!oldFqn.equals(ref.getQualifiedName())) return;
            int dot = newFqn.lastIndexOf('.');
            ref.setPackage(getFactory().Package().createReference(newFqn.substring(0, dot)));
            ref.setSimpleName(newFqn.substring(dot + 1));
        }
    }

    // ==================================================================
    // 3. Remove imports whose declaration (or package prefix) is now
    //    obsolete. Runs after the rename processors so that the
    //    auto-import writer doesn't re-materialize them.
    // ==================================================================
    static void cleanupObsoleteImports(CtModel model, List<String> obsoletePrefixes) {
        for (CtType<?> type : model.getAllTypes()) {
            CtCompilationUnit cu = type.getPosition().getCompilationUnit();
            if (cu == null) continue;
            List<CtImport> imports = cu.getImports();
            imports.removeIf(imp -> {
                String s = imp.toString();
                for (String p : obsoletePrefixes) {
                    if (s.contains(p)) return true;
                }
                return false;
            });
        }
    }

    // ==================================================================
    // 4. Widen `Connector` receivers to `ServerConnector` when the
    //    variable is assigned a ServerConnector or receives a call
    //    to one of the moved-away methods.
    // ==================================================================
    public static class ConnectorReceiverWideningProcessor extends AbstractProcessor<CtType<?>> {
        private final String oldInterface;
        private final String newConcrete;
        private final List<String> movedMethods;

        public ConnectorReceiverWideningProcessor(String oldInterface, String newConcrete,
                                                  List<String> movedMethods) {
            this.oldInterface = oldInterface;
            this.newConcrete = newConcrete;
            this.movedMethods = movedMethods;
        }

        @Override
        public void process(CtType<?> type) {
            List<String> receivers = new ArrayList<>();
            for (CtInvocation<?> inv : type.getElements(new TypeFilter<>(CtInvocation.class))) {
                String m = inv.getExecutable().getSimpleName();
                if (!movedMethods.contains(m)) continue;
                CtExpression<?> t = inv.getTarget();
                if (t instanceof CtVariableRead<?>) {
                    CtVariableReference<?> v = ((CtVariableRead<?>) t).getVariable();
                    if (v != null) receivers.add(v.getSimpleName());
                }
            }

            for (CtField<?> f : type.getFields()) {
                if (f.getType() == null) continue;
                if (!oldInterface.equals(f.getType().getQualifiedName())) continue;
                if (receivers.contains(f.getSimpleName())
                        || assignedFromConcrete(type, f.getSimpleName())) {
                    f.setType(getFactory().Type().createReference(newConcrete));
                }
            }

            for (CtLocalVariable<?> lv : type.getElements(new TypeFilter<>(CtLocalVariable.class))) {
                if (lv.getType() == null) continue;
                if (!oldInterface.equals(lv.getType().getQualifiedName())) continue;
                boolean needs = receivers.contains(lv.getSimpleName());
                if (!needs) {
                    CtExpression<?> init = lv.getDefaultExpression();
                    if (init instanceof CtConstructorCall<?>) {
                        CtTypeReference<?> rt = ((CtConstructorCall<?>) init).getType();
                        if (rt != null && newConcrete.equals(rt.getQualifiedName())) needs = true;
                    }
                }
                if (needs) lv.setType(getFactory().Type().createReference(newConcrete));
            }
        }

        private boolean assignedFromConcrete(CtType<?> type, String name) {
            for (CtAssignment<?, ?> a : type.getElements(new TypeFilter<>(CtAssignment.class))) {
                if (!(a.getAssigned() instanceof CtVariableRead<?>)) continue;
                CtVariableReference<?> v = ((CtVariableRead<?>) a.getAssigned()).getVariable();
                if (v == null || !name.equals(v.getSimpleName())) continue;
                CtExpression<?> rhs = a.getAssignment();
                if (rhs instanceof CtConstructorCall<?>) {
                    CtTypeReference<?> rt = ((CtConstructorCall<?>) rhs).getType();
                    if (rt != null && newConcrete.equals(rt.getQualifiedName())) return true;
                }
            }
            return false;
        }
    }

    // ==================================================================
    // 5. `new ServerConnector()` (i.e. the connector class from rule 2)
    //    needs the in-scope Server instance passed as its single
    //    argument, matching Jetty 11's `ServerConnector(Server)`.
    // ==================================================================
    public static class ServerConnectorConstructorArgumentProcessor extends AbstractProcessor<CtConstructorCall<?>> {
        private final String connectorClass;
        private final String serverClass;

        public ServerConnectorConstructorArgumentProcessor(String connectorClass, String serverClass) {
            this.connectorClass = connectorClass;
            this.serverClass = serverClass;
        }

        @Override
        public void process(CtConstructorCall<?> cc) {
            if (cc.getType() == null) return;
            if (!connectorClass.equals(cc.getType().getQualifiedName())) return;
            if (!cc.getArguments().isEmpty()) return;

            CtType<?> enclosing = cc.getParent(new TypeFilter<>(CtType.class));
            if (enclosing == null) return;

            // Prefer a field of the dependency Server type on the enclosing class.
            for (CtField<?> f : enclosing.getFields()) {
                if (f.getType() != null && serverClass.equals(f.getType().getQualifiedName())) {
                    cc.addArgument(buildFieldRead(f));
                    return;
                }
            }
            // Otherwise look for a Server-typed local variable in the same block
            // or a Server-typed parameter of the enclosing executable.
            CtElement cursor = cc;
            while (cursor != null && cursor != enclosing) {
                if (cursor instanceof CtBlock<?>) {
                    CtBlock<?> b = (CtBlock<?>) cursor;
                    for (CtStatement s : b.getStatements()) {
                        if (s instanceof CtLocalVariable<?>) {
                            CtLocalVariable<?> lv = (CtLocalVariable<?>) s;
                            if (lv.getType() != null
                                    && serverClass.equals(lv.getType().getQualifiedName())) {
                                cc.addArgument(
                                        getFactory().Code().createVariableRead(lv.getReference(), false));
                                return;
                            }
                        }
                    }
                }
                if (cursor instanceof CtMethod<?>) {
                    for (CtParameter<?> p : ((CtMethod<?>) cursor).getParameters()) {
                        if (p.getType() != null && serverClass.equals(p.getType().getQualifiedName())) {
                            cc.addArgument(
                                    getFactory().Code().createVariableRead(p.getReference(), false));
                            return;
                        }
                    }
                }
                if (cursor instanceof CtConstructor<?>) {
                    for (CtParameter<?> p : ((CtConstructor<?>) cursor).getParameters()) {
                        if (p.getType() != null && serverClass.equals(p.getType().getQualifiedName())) {
                            cc.addArgument(
                                    getFactory().Code().createVariableRead(p.getReference(), false));
                            return;
                        }
                    }
                }
                cursor = cursor.getParent();
            }
        }

        private CtExpression<?> buildFieldRead(CtField<?> f) {
            // Using a snippet here avoids CtFieldRead<T>.setVariable's field-generic friction.
            return getFactory().Code().createCodeSnippetExpression("this." + f.getSimpleName());
        }
    }

    // ==================================================================
    // 6. Rewrite server.setSend*(...) to httpConfiguration.setSend*(...).
    // Inserts `HttpConfiguration httpConfiguration = new HttpConfiguration();`
    // immediately before the first such call in the block, if absent.
    // ==================================================================
    public static class ServerSetterToHttpConfigurationProcessor extends AbstractProcessor<CtInvocation<?>> {
        private final String serverClass;
        private final String httpConfigClass;
        private final List<String> movedSetters;

        public ServerSetterToHttpConfigurationProcessor(String serverClass, String httpConfigClass,
                                                        List<String> movedSetters) {
            this.serverClass = serverClass;
            this.httpConfigClass = httpConfigClass;
            this.movedSetters = movedSetters;
        }

        @Override
        public void process(CtInvocation<?> inv) {
            String m = inv.getExecutable().getSimpleName();
            if (!movedSetters.contains(m)) return;
            CtExpression<?> target = inv.getTarget();
            if (target == null) return;
            if (!serverClass.equals(receiverType(target))) return;

            // The direct parent of a statement invocation is the containing block.
            CtElement grand = inv.getParent();
            if (!(grand instanceof CtBlock<?>)) return;
            CtBlock<?> block = (CtBlock<?>) grand;

            String varName = ensureHttpConfigDeclared(block);
            if (varName == null) return;

            // Replace the receiver with a snippet - avoids CtInvocation<T>.setTarget's
            // target-generic friction.
            CtExpression<?> newTarget = getFactory().Code()
                    .createCodeSnippetExpression(varName);
            inv.setTarget(newTarget);
        }

        private String receiverType(CtExpression<?> target) {
            if (target instanceof CtFieldRead<?>) {
                CtVariableReference<?> v = ((CtFieldRead<?>) target).getVariable();
                return v == null || v.getType() == null ? null : v.getType().getQualifiedName();
            }
            if (target instanceof CtVariableRead<?>) {
                CtVariableReference<?> v = ((CtVariableRead<?>) target).getVariable();
                return v == null || v.getType() == null ? null : v.getType().getQualifiedName();
            }
            return target.getType() == null ? null : target.getType().getQualifiedName();
        }

        private String ensureHttpConfigDeclared(CtBlock<?> block) {
            String varName = "httpConfiguration";
            for (CtStatement s : block.getStatements()) {
                if (s instanceof CtLocalVariable<?>) {
                    CtLocalVariable<?> lv = (CtLocalVariable<?>) s;
                    if (lv.getType() != null
                            && httpConfigClass.equals(lv.getType().getQualifiedName())) {
                        return lv.getSimpleName();
                    }
                }
            }
            List<CtStatement> stmts = block.getStatements();
            int insertIndex = -1;
            for (int i = 0; i < stmts.size(); i++) {
                if (stmts.get(i) instanceof CtInvocation<?>) {
                    CtInvocation<?> c = (CtInvocation<?>) stmts.get(i);
                    if (movedSetters.contains(c.getExecutable().getSimpleName())) {
                        insertIndex = i;
                        break;
                    }
                }
            }
            if (insertIndex < 0) return null;

            @SuppressWarnings("unchecked")
            CtTypeReference<Object> httpRef =
                    (CtTypeReference<Object>) getFactory().Type().createReference(httpConfigClass);
            CtConstructorCall<Object> newCall = getFactory().Code().createConstructorCall(httpRef);
            CtLocalVariable<Object> decl = getFactory().Code().createLocalVariable(httpRef, varName, newCall);
            stmts.get(insertIndex).insertBefore(decl);
            return varName;
        }
    }
}