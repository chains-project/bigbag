package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static final String FLYWAY_FQCN = "org.flywaydb.core.Flyway";
    private static final Pattern SETTER_PATTERN = Pattern.compile("^\\s*([A-Za-z_$][A-Za-z0-9_$]*)\\.([A-Za-z0-9_$]+)\\((.*)\\)\\s*;?\\s*$");
    private static final Map<String, String> SETTER_TO_FLUENT = new LinkedHashMap<>();

    static {
        SETTER_TO_FLUENT.put("setDataSource", "dataSource");
        SETTER_TO_FLUENT.put("setLocations", "locations");
        SETTER_TO_FLUENT.put("setLocationsAsStrings", "locations");
        SETTER_TO_FLUENT.put("setValidateOnMigrate", "validateOnMigrate");
        SETTER_TO_FLUENT.put("setShouldCreateSchemas", "createSchemas");
        SETTER_TO_FLUENT.put("setBatch", "batch");
        SETTER_TO_FLUENT.put("setCallbacks", "callbacks");
        SETTER_TO_FLUENT.put("setCleanDisabled", "cleanDisabled");
        SETTER_TO_FLUENT.put("setCleanOnValidationError", "cleanOnValidationError");
        SETTER_TO_FLUENT.put("setConnectRetries", "connectRetries");
        SETTER_TO_FLUENT.put("setConnectRetriesInterval", "connectRetriesInterval");
        SETTER_TO_FLUENT.put("setCreateSchemas", "createSchemas");
        SETTER_TO_FLUENT.put("setDefaultSchema", "defaultSchema");
        SETTER_TO_FLUENT.put("setDetectEncoding", "detectEncoding");
        SETTER_TO_FLUENT.put("setDriver", "driver");
        SETTER_TO_FLUENT.put("setDryRunOutput", "dryRunOutput");
        SETTER_TO_FLUENT.put("setDryRunOutputAsFile", "dryRunOutput");
        SETTER_TO_FLUENT.put("setDryRunOutputAsFileName", "dryRunOutput");
        SETTER_TO_FLUENT.put("setEncoding", "encoding");
        SETTER_TO_FLUENT.put("setEncodingAsString", "encoding");
        SETTER_TO_FLUENT.put("setErrorOverrides", "errorOverrides");
        SETTER_TO_FLUENT.put("setExecuteInTransaction", "executeInTransaction");
        SETTER_TO_FLUENT.put("setFailOnMissingLocations", "failOnMissingLocations");
        SETTER_TO_FLUENT.put("setGroup", "group");
        SETTER_TO_FLUENT.put("setIgnoreMigrationPatterns", "ignoreMigrationPatterns");
        SETTER_TO_FLUENT.put("setInitSql", "initSql");
        SETTER_TO_FLUENT.put("setInstalledBy", "installedBy");
        SETTER_TO_FLUENT.put("setJavaMigrationClassProvider", "javaMigrationClassProvider");
        SETTER_TO_FLUENT.put("setJavaMigrations", "javaMigrations");
        SETTER_TO_FLUENT.put("setJdbcProperties", "jdbcProperties");
        SETTER_TO_FLUENT.put("setKerberosConfigFile", "kerberosConfigFile");
        SETTER_TO_FLUENT.put("setLicenseKey", "licenseKey");
        SETTER_TO_FLUENT.put("setLockRetryCount", "lockRetryCount");
        SETTER_TO_FLUENT.put("setLoggers", "loggers");
        SETTER_TO_FLUENT.put("setMixed", "mixed");
        SETTER_TO_FLUENT.put("setOracleKerberosCacheFile", "oracleKerberosCacheFile");
        SETTER_TO_FLUENT.put("setOracleSqlplus", "oracleSqlplus");
        SETTER_TO_FLUENT.put("setOracleSqlplusWarn", "oracleSqlplusWarn");
        SETTER_TO_FLUENT.put("setOracleWalletLocation", "oracleWalletLocation");
        SETTER_TO_FLUENT.put("setOutOfOrder", "outOfOrder");
        SETTER_TO_FLUENT.put("setOutputQueryResults", "outputQueryResults");
        SETTER_TO_FLUENT.put("setPassword", "password");
        SETTER_TO_FLUENT.put("setPlaceholderPrefix", "placeholderPrefix");
        SETTER_TO_FLUENT.put("setPlaceholderReplacement", "placeholderReplacement");
        SETTER_TO_FLUENT.put("setPlaceholderSeparator", "placeholderSeparator");
        SETTER_TO_FLUENT.put("setPlaceholderSuffix", "placeholderSuffix");
        SETTER_TO_FLUENT.put("setPlaceholders", "placeholders");
        SETTER_TO_FLUENT.put("setRepeatableSqlMigrationPrefix", "repeatableSqlMigrationPrefix");
        SETTER_TO_FLUENT.put("setReportEnabled", "reportEnabled");
        SETTER_TO_FLUENT.put("setReportFilename", "reportFilename");
        SETTER_TO_FLUENT.put("setResolvers", "resolvers");
        SETTER_TO_FLUENT.put("setResourceProvider", "resourceProvider");
        SETTER_TO_FLUENT.put("setSchemas", "schemas");
        SETTER_TO_FLUENT.put("setScriptPlaceholderPrefix", "scriptPlaceholderPrefix");
        SETTER_TO_FLUENT.put("setScriptPlaceholderSuffix", "scriptPlaceholderSuffix");
        SETTER_TO_FLUENT.put("setSkipDefaultCallbacks", "skipDefaultCallbacks");
        SETTER_TO_FLUENT.put("setSkipDefaultResolvers", "skipDefaultResolvers");
        SETTER_TO_FLUENT.put("setSkipExecutingMigrations", "skipExecutingMigrations");
        SETTER_TO_FLUENT.put("setSqlMigrationPrefix", "sqlMigrationPrefix");
        SETTER_TO_FLUENT.put("setSqlMigrationSeparator", "sqlMigrationSeparator");
        SETTER_TO_FLUENT.put("setSqlMigrationSuffixes", "sqlMigrationSuffixes");
        SETTER_TO_FLUENT.put("setStream", "stream");
        SETTER_TO_FLUENT.put("setTable", "table");
        SETTER_TO_FLUENT.put("setTarget", "target");
        SETTER_TO_FLUENT.put("setUndoSqlMigrationPrefix", "undoSqlMigrationPrefix");
        SETTER_TO_FLUENT.put("setUrl", "url");
        SETTER_TO_FLUENT.put("setUser", "user");
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-root> <output-source-root>");
        }

        final Path inputRoot = Path.of(args[0]).toAbsolutePath().normalize();
        final Path outputRoot = Path.of(args[1]).toAbsolutePath().normalize();

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setSourceOutputDirectory(outputRoot.toFile());

        addSourceRoots(launcher, inputRoot);
        launcher.buildModel();
        rewriteFlywayInitializers(launcher);

        Files.createDirectories(outputRoot);
        launcher.prettyprint();
    }

    private static void addSourceRoots(Launcher launcher, Path root) throws Exception {
        try (var paths = Files.walk(root)) {
            paths.filter(Files::isDirectory)
                .filter(path -> {
                    final String normalized = path.toString().replace('\\', '/');
                    return normalized.endsWith("/src/main/java")
                        || normalized.endsWith("/src/test/java")
                        || normalized.endsWith("/src/it/java");
                })
                .forEach(path -> launcher.addInputResource(path.toString()));
        }
    }

    private static void rewriteFlywayInitializers(Launcher launcher) {
        for (CtLocalVariable<?> local : launcher.getModel().getElements(new TypeFilter<>(CtLocalVariable.class))) {
            if (!isFlywayType(local.getType())) {
                continue;
            }
            if (!isOldFlywayConstructor(local.getDefaultExpression())) {
                continue;
            }
            final CtBlock<?> block = local.getParent(CtBlock.class);
            if (block == null) {
                continue;
            }

            final List<CtStatement> statements = new ArrayList<>(block.getStatements());
            final int index = statements.indexOf(local);
            if (index < 0) {
                continue;
            }

            final List<CtStatement> setterStatements = new ArrayList<>();
            int cursor = index + 1;
            while (cursor < statements.size()) {
                final CtStatement statement = statements.get(cursor);
                final SetterCall setterCall = asSetterCall(statement, local.getSimpleName());
                if (setterCall == null) {
                    break;
                }
                if (!SETTER_TO_FLUENT.containsKey(setterCall.name)) {
                    break;
                }
                setterStatements.add(statement);
                cursor++;
            }

            if (setterStatements.isEmpty()) {
                continue;
            }

            @SuppressWarnings({"rawtypes", "unchecked"})
            final CtLocalVariable rawLocal = local;
            rawLocal.setDefaultExpression(createFlywayLoadExpression(local, setterStatements));
            for (CtStatement setterStatement : setterStatements) {
                setterStatement.delete();
            }
        }
    }

    private static SetterCall asSetterCall(CtStatement statement, String variableName) {
        final Matcher matcher = SETTER_PATTERN.matcher(statement.toString().trim());
        if (!matcher.matches() || !variableName.equals(matcher.group(1))) {
            return null;
        }
        return new SetterCall(matcher.group(2), matcher.group(3));
    }

    private static boolean isFlywayType(CtTypeReference<?> type) {
        return type != null && FLYWAY_FQCN.equals(type.getQualifiedName());
    }

    private static boolean isOldFlywayConstructor(CtExpression<?> expression) {
        if (!(expression instanceof CtConstructorCall<?>)) {
            return false;
        }
        final CtConstructorCall<?> ctor = (CtConstructorCall<?>) expression;
        final CtTypeReference<?> declaringType = ctor.getType();
        return declaringType != null
            && FLYWAY_FQCN.equals(declaringType.getQualifiedName())
            && ctor.getArguments().isEmpty();
    }

    private static CtExpression<?> createFlywayLoadExpression(CtLocalVariable<?> local, List<CtStatement> setterStatements) {
        String classLoaderArgs = null;
        for (CtStatement statement : setterStatements) {
            final SetterCall setterCall = asSetterCall(statement, local.getSimpleName());
            if (setterCall != null && "setClassLoader".equals(setterCall.name)) {
                classLoaderArgs = setterCall.args;
                break;
            }
        }

        final StringBuilder source = new StringBuilder("org.flywaydb.core.Flyway.configure(");
        if (classLoaderArgs != null && !classLoaderArgs.isBlank()) {
            source.append(classLoaderArgs.trim());
        }
        source.append(")");
        for (CtStatement statement : setterStatements) {
            final SetterCall setterCall = asSetterCall(statement, local.getSimpleName());
            if (setterCall == null) {
                continue;
            }
            if ("setClassLoader".equals(setterCall.name)) {
                continue;
            }
            source.append('.').append(mapSetter(setterCall.name)).append('(');
            if (!setterCall.args.isEmpty()) {
                final String[] parts = setterCall.args.split(",");
                for (int i = 0; i < parts.length; i++) {
                    if (i > 0) {
                        source.append(", ");
                    }
                    source.append(parts[i].trim());
                }
            }
            source.append(')');
        }
        source.append(".load()");
        return local.getFactory().Code().createCodeSnippetExpression(source.toString());
    }

    private static final class SetterCall {
        private final String name;
        private final String args;

        private SetterCall(String name, String args) {
            this.name = name;
            this.args = args;
        }
    }

    private static String mapSetter(String setterName) {
        final String mapped = SETTER_TO_FLUENT.get(setterName);
        if (mapped != null) {
            return mapped;
        }
        if (setterName.startsWith("set") && setterName.length() > 3) {
            final String suffix = setterName.substring(3);
            return Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
        }
        return setterName;
    }
}
