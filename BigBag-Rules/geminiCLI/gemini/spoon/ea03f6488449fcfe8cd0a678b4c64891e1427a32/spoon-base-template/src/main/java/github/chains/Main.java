package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.*;
import spoon.reflect.code.*;
import spoon.reflect.factory.Factory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.io.File;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) throws Exception {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/JRedisGraph/src/main/java/com");
        
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.buildModel();
        
        Factory factory = launcher.getFactory();

        // Remove unused imports
        for (CtCompilationUnit cu : factory.CompilationUnit().getMap().values()) {
            List<CtImport> toDelete = new ArrayList<>();
            for (CtImport imp : cu.getImports()) {
                String impStr = imp.toString();
                if (impStr.contains("BasicRedisPipeline") ||
                    impStr.contains("BinaryRedisPipeline") ||
                    impStr.contains("BinaryScriptingCommandsPipeline") ||
                    impStr.contains("ClusterPipeline") ||
                    impStr.contains("MultiKeyBinaryRedisPipeline") ||
                    impStr.contains("MultiKeyCommandsPipeline") ||
                    impStr.contains("RedisPipeline") ||
                    impStr.contains("ScriptingCommandsPipeline") ||
                    impStr.contains("redis.clients.jedis.Client")) {
                    toDelete.add(imp);
                }
            }
            for (CtImport imp : toDelete) {
                imp.delete();
            }
        }

        // 1. Update interfaces
        CtInterface<?> transInterface = factory.Interface().get("com.redislabs.redisgraph.RedisGraphTransaction");
        if (transInterface != null) {
            transInterface.setSuperInterfaces(new HashSet<>(Arrays.asList(
                factory.Type().createReference("redis.clients.jedis.commands.PipelineCommands"),
                factory.Type().createReference("redis.clients.jedis.commands.PipelineBinaryCommands"),
                factory.Type().createReference("java.io.Closeable")
            )));
        }

        CtInterface<?> pipeInterface = factory.Interface().get("com.redislabs.redisgraph.RedisGraphPipeline");
        if (pipeInterface != null) {
            pipeInterface.setSuperInterfaces(new HashSet<>(Arrays.asList(
                factory.Type().createReference("redis.clients.jedis.commands.PipelineCommands"),
                factory.Type().createReference("redis.clients.jedis.commands.PipelineBinaryCommands"),
                factory.Type().createReference("java.io.Closeable")
            )));
        }
        
        // 2. Update ContextedRedisGraph
        CtClass<?> ctxGraph = factory.Class().get("com.redislabs.redisgraph.impl.api.ContextedRedisGraph");
        for (CtMethod<?> m : ctxGraph.getMethods()) {
            if (m.getSimpleName().equals("multi")) {
                m.setBody(factory.Code().createCodeSnippetStatement(
                    "redis.clients.jedis.Jedis jedis = getConnection();\n" +
                    "RedisGraphTransaction transaction = new RedisGraphTransaction(jedis, this);\n" +
                    "transaction.setRedisGraphCaches(caches);\n" +
                    "return transaction"
                ));
            } else if (m.getSimpleName().equals("pipelined")) {
                m.setBody(factory.Code().createCodeSnippetStatement(
                    "redis.clients.jedis.Jedis jedis = getConnection();\n" +
                    "RedisGraphPipeline pipeline = new RedisGraphPipeline(jedis, this);\n" +
                    "pipeline.setRedisGraphCaches(caches);\n" +
                    "return pipeline"
                ));
            }
        }
        
        // 3. Update RedisGraphTransaction class
        CtClass<?> rgtImpl = factory.Class().get("com.redislabs.redisgraph.impl.api.RedisGraphTransaction");
        if (rgtImpl != null) {
            for (CtConstructor<?> cons : rgtImpl.getConstructors()) {
                if (cons.getParameters().size() == 2) {
                    cons.getParameters().get(0).setType(factory.Type().createReference("redis.clients.jedis.Jedis"));
                    cons.getParameters().get(0).setSimpleName("jedis");
                    cons.setBody(factory.Code().createCodeSnippetStatement("super(jedis);\nthis.redisGraph = redisGraph"));
                }
            }
            
            // execGetResponse() is already there or we added it, but let's make sure
            boolean hasMethod = false;
            for (CtMethod<?> m : rgtImpl.getMethods()) {
                if (m.getSimpleName().equals("execGetResponse")) hasMethod = true;
            }
            if (!hasMethod) {
                CtMethod<?> execGetRes = factory.Core().createMethod();
                execGetRes.setSimpleName("execGetResponse");
                execGetRes.setType(factory.Type().createReference("java.util.List"));
                execGetRes.setBody(factory.Code().createCodeSnippetStatement("throw new UnsupportedOperationException()"));
                execGetRes.addModifier(ModifierKind.PUBLIC);
                rgtImpl.addMethod(execGetRes);
            }
            
            replaceCommandUsage(rgtImpl, factory);
        }
        
        // 4. Update RedisGraphPipeline class
        CtClass<?> rgpImpl = factory.Class().get("com.redislabs.redisgraph.impl.api.RedisGraphPipeline");
        if (rgpImpl != null) {
            for (CtConstructor<?> cons : rgpImpl.getConstructors()) {
                if (cons.getParameters().size() == 2) {
                    cons.getParameters().get(0).setType(factory.Type().createReference("redis.clients.jedis.Jedis"));
                    cons.getParameters().get(0).setSimpleName("jedis");
                    cons.setBody(factory.Code().createCodeSnippetStatement("super(jedis);\nthis.redisGraph = redisGraph"));
                }
            }
            replaceCommandUsage(rgpImpl, factory);
        }

        // Print to file
        launcher.setSourceOutputDirectory(new File("/workspace/JRedisGraph/src/main/java"));
        launcher.prettyprint();
        System.out.println("Transformation complete.");
    }
    
    private static void replaceCommandUsage(CtClass<?> clazz, Factory factory) {
        for (CtMethod<?> m : clazz.getMethods()) {
            if (m.getBody() == null) continue;
            
            boolean isQuery = m.getSimpleName().equals("query") || m.getSimpleName().equals("readOnlyQuery");
            boolean isDelete = m.getSimpleName().equals("deleteGraph");
            
            if (isQuery) {
                boolean hasParams = false;
                boolean hasArgs = false;
                for (CtParameter<?> p : m.getParameters()) {
                    if (p.getSimpleName().equals("params")) hasParams = true;
                    if (p.getSimpleName().equals("args")) hasArgs = true;
                }
                boolean hasTimeout = false;
                for (CtParameter<?> p : m.getParameters()) {
                    if (p.getSimpleName().equals("timeout")) hasTimeout = true;
                }
                
                String cmd = m.getSimpleName().equals("query") ? "RedisGraphCommand.QUERY" : "RedisGraphCommand.RO_QUERY";
                
                String snippet = "";
                if (hasParams && hasTimeout) {
                    snippet = "String preparedQuery = Utils.prepareQuery(query, params);\n" +
                              "return appendCommand(new redis.clients.jedis.CommandObject<>(new redis.clients.jedis.CommandArguments(" + cmd + ").addObjects(graphId, preparedQuery, Utils.COMPACT_STRING, Utils.TIMEOUT_STRING, Long.toString(timeout)), new redis.clients.jedis.Builder<ResultSet>() {\n" +
                              "    @SuppressWarnings(\"unchecked\")\n" +
                              "    @Override\n" +
                              "    public ResultSet build(Object o) {\n" +
                              "        return new com.redislabs.redisgraph.impl.resultset.ResultSetImpl((List<Object>) o, redisGraph, caches.getGraphCache(graphId));\n" +
                              "    }\n" +
                              "}))";
                } else if (hasParams) {
                    snippet = "String preparedQuery = Utils.prepareQuery(query, params);\n" +
                              "return appendCommand(new redis.clients.jedis.CommandObject<>(new redis.clients.jedis.CommandArguments(" + cmd + ").addObjects(graphId, preparedQuery, Utils.COMPACT_STRING), new redis.clients.jedis.Builder<ResultSet>() {\n" +
                              "    @SuppressWarnings(\"unchecked\")\n" +
                              "    @Override\n" +
                              "    public ResultSet build(Object o) {\n" +
                              "        return new com.redislabs.redisgraph.impl.resultset.ResultSetImpl((List<Object>) o, redisGraph, caches.getGraphCache(graphId));\n" +
                              "    }\n" +
                              "}))";
                } else if (hasArgs) {
                    snippet = "String preparedQuery = Utils.prepareQuery(query, args);\n" +
                              "return appendCommand(new redis.clients.jedis.CommandObject<>(new redis.clients.jedis.CommandArguments(" + cmd + ").addObjects(graphId, preparedQuery, Utils.COMPACT_STRING), new redis.clients.jedis.Builder<ResultSet>() {\n" +
                              "    @SuppressWarnings(\"unchecked\")\n" +
                              "    @Override\n" +
                              "    public ResultSet build(Object o) {\n" +
                              "        return new com.redislabs.redisgraph.impl.resultset.ResultSetImpl((List<Object>) o, redisGraph, caches.getGraphCache(graphId));\n" +
                              "    }\n" +
                              "}))";
                } else if (hasTimeout) {
                    snippet = "return appendCommand(new redis.clients.jedis.CommandObject<>(new redis.clients.jedis.CommandArguments(" + cmd + ").addObjects(graphId, query, Utils.COMPACT_STRING, Utils.TIMEOUT_STRING, Long.toString(timeout)), new redis.clients.jedis.Builder<ResultSet>() {\n" +
                              "    @SuppressWarnings(\"unchecked\")\n" +
                              "    @Override\n" +
                              "    public ResultSet build(Object o) {\n" +
                              "        return new com.redislabs.redisgraph.impl.resultset.ResultSetImpl((List<Object>) o, redisGraph, caches.getGraphCache(graphId));\n" +
                              "    }\n" +
                              "}))";
                } else {
                    snippet = "return appendCommand(new redis.clients.jedis.CommandObject<>(new redis.clients.jedis.CommandArguments(" + cmd + ").addObjects(graphId, query, Utils.COMPACT_STRING), new redis.clients.jedis.Builder<ResultSet>() {\n" +
                              "    @SuppressWarnings(\"unchecked\")\n" +
                              "    @Override\n" +
                              "    public ResultSet build(Object o) {\n" +
                              "        return new com.redislabs.redisgraph.impl.resultset.ResultSetImpl((List<Object>) o, redisGraph, caches.getGraphCache(graphId));\n" +
                              "    }\n" +
                              "}))";
                }
                m.setBody(factory.Code().createCodeSnippetStatement(snippet));
            } else if (isDelete) {
                String snippet = "redis.clients.jedis.Response<String> response = appendCommand(new redis.clients.jedis.CommandObject<>(new redis.clients.jedis.CommandArguments(RedisGraphCommand.DELETE).addObjects(graphId), redis.clients.jedis.BuilderFactory.STRING));\n" +
                                 "caches.removeGraphCache(graphId);\n" +
                                 "return response";
                m.setBody(factory.Code().createCodeSnippetStatement(snippet));
            }
        }
    }
}
