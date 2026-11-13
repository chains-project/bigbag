package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * GENERIC, reusable transformation rule for the flyway-core 8.x -> 9.x breaking change
 * (e.g. org.flywaydb:flyway-core 4.x/5.x/6.x/7.x/8.x -> 9.16.3).
 *
 * Old API pattern - mutable bean, no-arg constructor + JavaBean setters:
 *     T v = new T();
 *     v.setX(a);
 *     v.setY(b);
 *     // v used afterwards
 *
 * New API pattern - fluent configuration builder obtained from a static factory,
 * finalized by a terminal call:
 *     T v = T.configure().x(a).y(b).load();
 *
 * Structural facts about the dependency (parameterized below, NOT from any client):
 *   - affected type:           org.flywaydb.core.Flyway (public no-arg ctor removed)
 *   - static factory:          Flyway.configure() / Flyway.configure(ClassLoader)
 *   - builder type:            org.flywaydb.core.api.configuration.FluentConfiguration
 *   - builder methods:         same names as the old setters minus the "set" prefix,
 *                              first letter lower-cased (setDataSource -> dataSource, ...)
 *   - exception:               setClassLoader has NO fluent builder method; the
 *                              ClassLoader is passed to the configure(ClassLoader) overload
 *   - terminal method:         load() returns the affected type
 *
 * The rule matches ONLY the dependency-side pattern (type FQN + "new T()" + setter calls
 * on the just-created variable); all client identifiers (variable names, argument
 * expressions, enclosing classes/methods) are discovered structurally. Apply it to any
 * Maven project by passing the project's source directory:  java github.chains.Main <src-root>
 */
public class Main {

    // ---- Rule parameters: fully-qualified types and signatures from the DEPENDENCY ----

    /** Dependency class whose public no-arg constructor and JavaBean setters were removed. */
    private static final String AFFECTED_TYPE = "org.flywaydb.core.Flyway";

    /** Static factory method on the affected type that returns the configuration builder. */
    private static final String FACTORY_METHOD = "configure";

    /** Terminal builder method that produces an instance of the affected type. */
    private static final String TERMINAL_METHOD = "load";

    /**
     * Old setter with no same-named fluent builder method; its argument is routed through
     * the builder-accepting factory overload: v.setX(cl) => T.configure(cl).
     */
    private static final String FACTORY_ARG_SETTER = "setClassLoader";

    /** Public fluent methods of the dependency's configuration builder
     *  (org.flywaydb.core.api.configuration.FluentConfiguration in 9.16.3). */
    private static final Set<String> BUILDER_METHODS = new HashSet<>(Arrays.asList(
            "baselineDescription", "baselineOnMigrate", "baselineVersion", "batch", "callbacks", "cherryPick",
            "cleanDisabled", "cleanOnValidationError", "configuration", "connectRetries", "connectRetriesInterval",
            "createSchemas", "dataSource", "defaultSchema", "detectEncoding", "dryRunOutput", "encoding", "envVars",
            "errorOverrides", "executeInTransaction", "failOnMissingLocations", "group", "ignoreMigrationPatterns",
            "initSql", "installedBy", "javaMigrationClassProvider", "javaMigrations", "jdbcProperties",
            "kerberosConfigFile", "licenseKey", "loadDefaultConfigurationFiles", "locations", "lockRetryCount",
            "loggers", "mixed", "oracleKerberosCacheFile", "oracleSqlplus", "oracleSqlplusWarn",
            "oracleWalletLocation", "outOfOrder", "outputQueryResults", "placeholderPrefix", "placeholderReplacement",
            "placeholders", "placeholderSeparator", "placeholderSuffix", "repeatableSqlMigrationPrefix",
            "reportFilename", "resolvers", "resourceProvider", "schemas", "scriptPlaceholderPrefix",
            "scriptPlaceholderSuffix", "skipDefaultCallbacks", "skipDefaultResolvers", "skipExecutingMigrations",
            "sqlMigrationPrefix", "sqlMigrationSeparator", "sqlMigrationSuffixes", "stream", "table", "tablespace",
            "target", "undoSqlMigrationPrefix", "validateMigrationNaming", "validateOnMigrate"));

    public static void main(final String[] args) throws IOException {
        final Path root = Paths.get(args.length > 0 ? args[0] : ".");
        StaticJavaParser.setConfiguration(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17));
        final List<Path> files;
        try (final Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        int transformedFiles = 0;
        int totalSites = 0;
        for (final Path file : files) {
            final int sites = transformFile(file);
            if (sites > 0) {
                transformedFiles++;
                totalSites += sites;
                System.out.println("TRANSFORMED " + sites + " call site(s): " + file);
            }
        }
        System.out.println("DONE: " + transformedFiles + " file(s), " + totalSites
                + " call site(s) transformed under " + root);
    }

    private static int transformFile(final Path file) {
        final CompilationUnit cu;
        try {
            cu = LexicalPreservingPrinter.setup(StaticJavaParser.parse(file));
        } catch (final Exception e) {
            System.out.println("WARN: could not parse " + file + " (" + e.getMessage() + ")");
            return 0;
        }
        if (!referencesAffectedType(cu)) {
            return 0;
        }
        int count = 0;
        for (final ObjectCreationExpr creation : new ArrayList<>(cu.findAll(ObjectCreationExpr.class))) {
            if (transformCreation(creation, cu)) {
                count++;
            }
        }
        // any remaining bare "new T()" (e.g. "return new Flyway();", field initializers) has no
        // trailing setters to fold in: replace the expression directly with T.configure().load()
        for (final ObjectCreationExpr creation : new ArrayList<>(cu.findAll(ObjectCreationExpr.class))) {
            if (creation.getArguments().isEmpty() && isAffectedType(creation.getType(), cu)
                    && creation.findAncestor(CompilationUnit.class).filter(a -> a == cu).isPresent()) {
                creation.replace(StaticJavaParser.parseExpression(
                        typeReferenceFor(creation, cu) + "." + FACTORY_METHOD + "()." + TERMINAL_METHOD + "()"));
                count++;
            }
        }
        if (count > 0) {
            try {
                Files.write(file, LexicalPreservingPrinter.print(cu).getBytes(StandardCharsets.UTF_8));
            } catch (final IOException e) {
                System.out.println("ERROR writing " + file + ": " + e.getMessage());
            }
        }
        return count;
    }

    /** Does this file reference the dependency's affected type (via import or fully-qualified name)? */
    private static boolean referencesAffectedType(final CompilationUnit cu) {
        for (final ImportDeclaration imp : cu.getImports()) {
            if (imp.getNameAsString().equals(AFFECTED_TYPE)
                    || (imp.isAsterisk() && imp.getNameAsString().equals(packageName(AFFECTED_TYPE)))) {
                return true;
            }
        }
        return cu.findAll(ClassOrInterfaceType.class).stream()
                .anyMatch(t -> t.toString().equals(AFFECTED_TYPE));
    }

    private static boolean isAffectedType(final ClassOrInterfaceType type, final CompilationUnit cu) {
        if (!type.getNameAsString().equals(simpleName(AFFECTED_TYPE))) {
            return false;
        }
        return type.toString().equals(AFFECTED_TYPE) || referencesAffectedType(cu);
    }

    /**
     * Pattern (matched structurally, independent of client code):
     *   <mods> T var = new T();                 // T is the affected dependency type
     *   var.setX(...); var.setY(...);           // later sibling statements in the same block
     * Rewrite the initializer to the fluent builder chain and remove the setter statements.
     */
    private static boolean transformCreation(final ObjectCreationExpr creation, final CompilationUnit cu) {
        // "new T()" with the removed no-arg signature
        if (!creation.getArguments().isEmpty() || !isAffectedType(creation.getType(), cu)) {
            return false;
        }
        // must be the initializer of a local variable declaration in a block
        if (!(creation.getParentNode().orElse(null) instanceof VariableDeclarator)) {
            return false;
        }
        final VariableDeclarator declarator = (VariableDeclarator) creation.getParentNode().get();
        if (!(declarator.getParentNode().orElse(null) instanceof VariableDeclarationExpr)) {
            return false;
        }
        final VariableDeclarationExpr declaration = (VariableDeclarationExpr) declarator.getParentNode().get();
        if (declaration.getVariables().size() != 1
                || !(declaration.getParentNode().orElse(null) instanceof ExpressionStmt)) {
            return false;
        }
        final ExpressionStmt declarationStmt = (ExpressionStmt) declaration.getParentNode().get();
        if (!(declarationStmt.getParentNode().orElse(null) instanceof BlockStmt)) {
            return false;
        }
        // safety: node must still be attached to this compilation unit (not to a removed subtree)
        if (!declarationStmt.findAncestor(CompilationUnit.class).filter(a -> a == cu).isPresent()) {
            return false;
        }
        final BlockStmt block = (BlockStmt) declarationStmt.getParentNode().get();
        final String varName = declarator.getNameAsString();

        // collect "var.setX(...)" sibling statements, in source order
        final List<MethodCallExpr> matchedCalls = new ArrayList<>();
        final List<ExpressionStmt> matchedStmts = new ArrayList<>();
        for (final Node child : block.getChildNodes()) {
            if (!(child instanceof ExpressionStmt) || child == declarationStmt) {
                continue;
            }
            final ExpressionStmt stmt = (ExpressionStmt) child;
            if (!(stmt.getExpression() instanceof MethodCallExpr)) {
                continue;
            }
            final MethodCallExpr call = (MethodCallExpr) stmt.getExpression();
            if (isRemovedSetter(call.getNameAsString()) && receivesVar(call, varName)) {
                matchedCalls.add(call);
                matchedStmts.add(stmt);
            }
        }

        // rebuild: T.configure([classLoaderArg]) .fluent(args)... .load()
        final String typeRef = typeReferenceFor(creation, cu);
        final StringBuilder chain = new StringBuilder(typeRef).append(".").append(FACTORY_METHOD).append("(");
        final boolean[] consumed = new boolean[matchedCalls.size()];
        for (int i = 0; i < matchedCalls.size(); i++) {
            final MethodCallExpr call = matchedCalls.get(i);
            if (call.getNameAsString().equals(FACTORY_ARG_SETTER) && !call.getArguments().isEmpty()) {
                chain.append(argList(call));
                consumed[i] = true;
                break; // only the first occurrence maps to the factory overload
            }
        }
        chain.append(")");
        for (int i = 0; i < matchedCalls.size(); i++) {
            final MethodCallExpr call = matchedCalls.get(i);
            final String fluent = fluentNameFor(call.getNameAsString());
            if (fluent == null) {
                continue;
            }
            chain.append(".").append(fluent).append("(").append(argList(call)).append(")");
            consumed[i] = true;
        }
        chain.append(".").append(TERMINAL_METHOD).append("()");

        declarator.setInitializer(StaticJavaParser.parseExpression(chain.toString()));
        for (int i = 0; i < matchedCalls.size(); i++) {
            if (consumed[i]) {
                matchedStmts.get(i).remove();
            }
        }
        return true;
    }

    private static String typeReferenceFor(final ObjectCreationExpr creation, final CompilationUnit cu) {
        final String written = creation.getType().toString();
        if (written.equals(AFFECTED_TYPE) || hasImportFor(cu)) {
            return written;
        }
        return AFFECTED_TYPE; // simple name used without a visible import -> qualify it
    }

    private static boolean hasImportFor(final CompilationUnit cu) {
        return cu.getImports().stream().anyMatch(i -> i.getNameAsString().equals(AFFECTED_TYPE)
                || (i.isAsterisk() && i.getNameAsString().equals(packageName(AFFECTED_TYPE))));
    }

    private static String argList(final MethodCallExpr call) {
        return call.getArguments().stream().map(Expression::toString).collect(Collectors.joining(", "));
    }

    /** true if the call name is a JavaBean setter removed from the affected type. */
    private static boolean isRemovedSetter(final String name) {
        if (name.equals(FACTORY_ARG_SETTER)) {
            return true;
        }
        final String derived = deriveFluentName(name);
        return derived != null && BUILDER_METHODS.contains(derived);
    }

    /** the fluent builder method replacing an old setter; null if the setter maps to the factory overload. */
    private static String fluentNameFor(final String setter) {
        if (setter.equals(FACTORY_ARG_SETTER)) {
            return null;
        }
        final String derived = deriveFluentName(setter);
        return derived != null && BUILDER_METHODS.contains(derived) ? derived : null;
    }

    /** setFooBar -> fooBar (JavaBean de-prefixing); null if the name is not a setter. */
    private static String deriveFluentName(final String setter) {
        if (!setter.startsWith("set") || setter.length() <= 3) {
            return null;
        }
        return Character.toLowerCase(setter.charAt(3)) + setter.substring(4);
    }

    /** receiver is `var` or `this.var`. */
    private static boolean receivesVar(final MethodCallExpr call, final String varName) {
        final Optional<Expression> scope = call.getScope();
        if (!scope.isPresent()) {
            return false;
        }
        if (scope.get() instanceof NameExpr) {
            return ((NameExpr) scope.get()).getNameAsString().equals(varName);
        }
        if (scope.get() instanceof FieldAccessExpr) {
            final FieldAccessExpr access = (FieldAccessExpr) scope.get();
            return access.getNameAsString().equals(varName) && access.getScope() instanceof NameExpr
                    && ((NameExpr) access.getScope()).getNameAsString().equals("this");
        }
        return false;
    }

    private static String simpleName(final String fqn) {
        final int i = fqn.lastIndexOf('.');
        return i < 0 ? fqn : fqn.substring(i + 1);
    }

    private static String packageName(final String fqn) {
        final int i = fqn.lastIndexOf('.');
        return i < 0 ? "" : fqn.substring(0, i);
    }
}
