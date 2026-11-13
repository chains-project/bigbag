package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transform.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            processDirectory(Paths.get(sourceDir));
            System.out.println("Transformation completed successfully.");
        } catch (IOException e) {
            System.err.println("Error processing directory: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(Main::processFile);
        }
    }
    
    private static void processFile(Path filePath) {
        try {
            JavaParser parser = new JavaParser();
            Optional<CompilationUnit> cuOpt = parser.parse(filePath).getResult();
            
            if (cuOpt.isPresent()) {
                CompilationUnit cu = cuOpt.get();
                
                // Create and apply the transformation visitor
                JedisApiTransformer transformer = new JedisApiTransformer();
                cu.accept(transformer, null);
                
                // Write the modified file back
                Files.write(filePath, cu.toString().getBytes());
                System.out.println("Processed: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class JedisApiTransformer extends ModifierVisitor<Void> {
        
        @Override
        public Node visit(ImportDeclaration id, Void arg) {
            // Remove redis.clients.jedis.Client import if present
            if (id.getNameAsString().equals("redis.clients.jedis.Client")) {
                return null; // Remove the import
            }
            return (Node) super.visit(id, arg);
        }
        
        @Override
        public Node visit(MethodCallExpr n, Void arg) {
            // Pattern 1: jedis.getClient() -> jedis
            if (n.getNameAsString().equals("getClient")) {
                if (n.getScope().isPresent()) {
                    String scope = n.getScope().get().toString();
                    if (scope.equals("jedis") || scope.endsWith(".getClient")) {
                        // Replace getClient() call with the scope (jedis)
                        return new NameExpr(scope);
                    }
                }
            }
            
            // Pattern 2: client.sendCommand(...) -> sendCommand(...)
            if (n.getScope().isPresent() && n.getScope().get().toString().equals("client")) {
                if (n.getNameAsString().equals("sendCommand")) {
                    n.setScope(null);
                }
            }
            
            // Pattern 3: getResponse(...) -> enqueResponse(...)
            if (n.getNameAsString().equals("getResponse")) {
                n.setName("enqueResponse");
            }
            
            // Pattern 4: client.multi() -> multi()
            if (n.getScope().isPresent() && n.getScope().get().toString().equals("client")) {
                if (n.getNameAsString().equals("multi") || n.getNameAsString().equals("getOne")) {
                    n.setScope(null);
                }
            }
            
            return (Node) super.visit(n, arg);
        }
        
        @Override
        public Node visit(ObjectCreationExpr n, Void arg) {
            // Pattern: new Pipeline(client) -> new Pipeline(jedis)
            // Pattern: new Transaction(client) -> new Transaction(jedis)
            String typeName = n.getTypeAsString();
            if (typeName.equals("Pipeline") || typeName.equals("Transaction") ||
                typeName.equals("RedisGraphPipeline") || typeName.equals("RedisGraphTransaction")) {
                
                // If constructor has one argument and it's "client", we need to check
                // if it should be changed to "jedis" 
                // This is complex because we need to understand the context
            }
            
            return (Node) super.visit(n, arg);
        }
    }
}