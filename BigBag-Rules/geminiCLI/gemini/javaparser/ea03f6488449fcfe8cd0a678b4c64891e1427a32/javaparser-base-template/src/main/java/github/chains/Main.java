package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        modifyInterfaces("/workspace/JRedisGraph/src/main/java/com/redislabs/redisgraph/RedisGraphTransaction.java", true);
        modifyInterfaces("/workspace/JRedisGraph/src/main/java/com/redislabs/redisgraph/RedisGraphPipeline.java", false);
        
        modifyContextedRedisGraph();
        modifyImplClass("/workspace/JRedisGraph/src/main/java/com/redislabs/redisgraph/impl/api/RedisGraphTransaction.java");
        modifyImplClass("/workspace/JRedisGraph/src/main/java/com/redislabs/redisgraph/impl/api/RedisGraphPipeline.java");
        
        System.out.println("Transformation complete.");
    }

    public static void modifyInterfaces(String filePath, boolean removeExecGetResponse) throws Exception {
        CompilationUnit cu = StaticJavaParser.parse(new File(filePath));
        ClassOrInterfaceDeclaration decl = cu.findAll(ClassOrInterfaceDeclaration.class).get(0);
        decl.getExtendedTypes().clear();
        decl.addExtendedType("redis.clients.jedis.commands.PipelineCommands");
        decl.addExtendedType("redis.clients.jedis.commands.PipelineBinaryCommands");
        decl.addExtendedType("redis.clients.jedis.commands.RedisModulePipelineCommands");
        if (filePath.contains("Pipeline")) {
            decl.addExtendedType("redis.clients.jedis.commands.DatabasePipelineCommands");
        }
        decl.addExtendedType("java.io.Closeable");

        if (removeExecGetResponse) {
            cu.findAll(MethodDeclaration.class).stream()
                .filter(m -> m.getNameAsString().equals("execGetResponse"))
                .forEach(m -> m.remove());
        }
        
        cu.getImports().removeIf(i -> i.getNameAsString().contains("redis.clients.jedis.commands") && !i.getNameAsString().endsWith("Commands"));
        Files.write(Paths.get(filePath), cu.toString().getBytes());
    }

    public static void modifyContextedRedisGraph() throws Exception {
        String filePath = "/workspace/JRedisGraph/src/main/java/com/redislabs/redisgraph/impl/api/ContextedRedisGraph.java";
        CompilationUnit cu = StaticJavaParser.parse(new File(filePath));
        
        cu.findAll(MethodDeclaration.class).stream()
            .filter(m -> m.getNameAsString().equals("multi"))
            .forEach(m -> {
                m.setBody(StaticJavaParser.parseBlock(
                    "{ Jedis jedis = getConnection(); " +
                    "RedisGraphTransaction transaction = new RedisGraphTransaction(jedis.getConnection(), this); " +
                    "transaction.multi(); " +
                    "transaction.setRedisGraphCaches(caches); " +
                    "return transaction; }"
                ));
            });

        cu.findAll(MethodDeclaration.class).stream()
            .filter(m -> m.getNameAsString().equals("pipelined"))
            .forEach(m -> {
                m.setBody(StaticJavaParser.parseBlock(
                    "{ Jedis jedis = getConnection(); " +
                    "RedisGraphPipeline pipeline = new RedisGraphPipeline(jedis.getConnection(), this); " +
                    "pipeline.setRedisGraphCaches(caches); " +
                    "return pipeline; }"
                ));
            });

        cu.getImports().removeIf(i -> i.getNameAsString().equals("redis.clients.jedis.Client"));
        Files.write(Paths.get(filePath), cu.toString().getBytes());
    }

    public static void modifyImplClass(String filePath) throws Exception {
        CompilationUnit cu = StaticJavaParser.parse(new File(filePath));
        
        cu.findAll(ConstructorDeclaration.class).forEach(c -> {
            if (c.getParameters().size() > 0 && (c.getParameter(0).getType().asString().equals("Client") || c.getParameter(0).getType().asString().equals("redis.clients.jedis.Client"))) {
                c.getParameter(0).setType("redis.clients.jedis.Connection");
                c.getParameter(0).setName("connection");
                c.getBody().getStatements().clear();
                c.getBody().addStatement(StaticJavaParser.parseExplicitConstructorInvocationStmt("super(connection);"));
                c.getBody().addStatement(StaticJavaParser.parseStatement("this.redisGraph = redisGraph;"));
            }
        });

        cu.findAll(MethodDeclaration.class).forEach(m -> {
            String name = m.getNameAsString();
            if (name.equals("query") || name.equals("readOnlyQuery")) {
                String cmd = name.equals("query") ? "RedisGraphCommand.QUERY" : "RedisGraphCommand.RO_QUERY";
                String qVar = m.getParameters().stream().anyMatch(p -> p.getNameAsString().equals("preparedQuery")) ? "preparedQuery" : "query";
                
                String bodyStr = "{ " +
                    (m.getParameters().stream().anyMatch(p -> p.getNameAsString().equals("params")) ? "String preparedQuery = Utils.prepareQuery(query, params); " : "") +
                    (m.getParameters().stream().anyMatch(p -> p.getNameAsString().equals("args")) ? "String preparedQuery = Utils.prepareQuery(query, args); " : "") +
                    "redis.clients.jedis.CommandArguments cmdArgs = new redis.clients.jedis.CommandArguments(" + cmd + ").key(graphId).add(" + qVar + ").add(Utils.COMPACT_STRING); ";
                
                if (m.getParameters().stream().anyMatch(p -> p.getNameAsString().equals("timeout"))) {
                    bodyStr += "cmdArgs.add(Utils.TIMEOUT_STRING).add(Long.toString(timeout)); ";
                }
                
                bodyStr += "return appendCommand(new redis.clients.jedis.CommandObject<>(cmdArgs, new Builder<ResultSet>() { " +
                           "@SuppressWarnings(\"unchecked\") " +
                           "@Override " +
                           "public ResultSet build(Object o) { " +
                           "    return new ResultSetImpl((List<Object>) o, redisGraph, caches.getGraphCache(graphId)); " +
                           "} " +
                           "})); }";
                
                m.setBody(StaticJavaParser.parseBlock(bodyStr));
            } else if (name.equals("deleteGraph")) {
                m.setBody(StaticJavaParser.parseBlock(
                    "{ Response<String> response = appendCommand(new redis.clients.jedis.CommandObject<>(new redis.clients.jedis.CommandArguments(RedisGraphCommand.DELETE).key(graphId), redis.clients.jedis.BuilderFactory.STRING)); caches.removeGraphCache(graphId); return response; }"
                ));
            }
        });
        
        cu.getImports().removeIf(i -> i.getNameAsString().equals("redis.clients.jedis.Client"));
        Files.write(Paths.get(filePath), cu.toString().getBytes());
    }
}
