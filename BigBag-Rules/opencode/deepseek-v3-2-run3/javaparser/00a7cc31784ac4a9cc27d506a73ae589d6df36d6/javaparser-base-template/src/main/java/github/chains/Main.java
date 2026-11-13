package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class Main {
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming files in: " + sourceDir);
        
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
            
        AtomicInteger transformedFiles = new AtomicInteger(0);
        AtomicInteger transformations = new AtomicInteger(0);
        
        for (Path javaFile : javaFiles) {
            try {
                JavaParser parser = new JavaParser();
                CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
                
                if (cu == null) {
                    continue;
                }
                
                AtomicInteger fileModified = new AtomicInteger(0);
                
                // Pattern 1: Remove setUseClientMode() method calls
                cu.accept(new VoidVisitorAdapter<Void>() {
                    @Override
                    public void visit(MethodCallExpr n, Void arg) {
                        super.visit(n, arg);
                        
                        // Match calls like: sslFilter.setUseClientMode(true/false)
                        if (n.getNameAsString().equals("setUseClientMode") && 
                            n.getArguments().size() == 1) {
                            
                            // Check if it's called on an SslFilter or SSLFilter instance
                            boolean isSslFilterCall = false;
                            if (n.getScope().isPresent()) {
                                String scopeName = n.getScope().get().toString();
                                if (scopeName.toLowerCase().contains("sslfilter") || 
                                    scopeName.contains("sslFilter") ||
                                    scopeName.contains("SSLFilter")) {
                                    isSslFilterCall = true;
                                }
                            }
                            
                            if (isSslFilterCall) {
                                System.out.println("Removing setUseClientMode call at: " + 
                                    javaFile + ":" + n.getRange().map(r -> r.begin.line).orElse(0));
                                n.remove();
                                fileModified.incrementAndGet();
                                transformations.incrementAndGet();
                            }
                        }
                    }
                }, null);
                
                // Pattern 2: Fix SslFilter constructor - remove boolean autoStart parameter
                cu.accept(new VoidVisitorAdapter<Void>() {
                    @Override
                    public void visit(ObjectCreationExpr n, Void arg) {
                        super.visit(n, arg);
                        
                        // Match new SslFilter(...) or new SSLFilter(...)
                        String typeName = n.getTypeAsString();
                        if (typeName.equals("SslFilter") || typeName.equals("SSLFilter")) {
                            // Check constructor signature: SslFilter(SSLContext, boolean)
                            if (n.getArguments().size() == 2) {
                                // Second argument should be a boolean literal or boolean expression
                                String secondArg = n.getArgument(1).toString();
                                if (secondArg.equals("true") || secondArg.equals("false") || 
                                    secondArg.equals("autoStart") || secondArg.matches(".*[Aa]uto[Ss]tart.*")) {
                                    
                                    System.out.println("Fixing SslFilter constructor at: " + 
                                        javaFile + ":" + n.getRange().map(r -> r.begin.line).orElse(0));
                                    n.getArguments().remove(1);
                                    fileModified.incrementAndGet();
                                    transformations.incrementAndGet();
                                }
                            }
                        }
                    }
                }, null);
                
                // Pattern 3: Remove initiateHandshake() method calls
                cu.accept(new VoidVisitorAdapter<Void>() {
                    @Override
                    public void visit(MethodCallExpr n, Void arg) {
                        super.visit(n, arg);
                        
                        // Match calls like: sslFilter.initiateHandshake(session)
                        if (n.getNameAsString().equals("initiateHandshake") && 
                            n.getArguments().size() == 1) {
                            
                            // Check if it's called on an SslFilter or SSLFilter instance
                            boolean isSslFilterCall = false;
                            if (n.getScope().isPresent()) {
                                String scopeName = n.getScope().get().toString();
                                if (scopeName.toLowerCase().contains("sslfilter") || 
                                    scopeName.contains("sslFilter") ||
                                    scopeName.contains("SSLFilter")) {
                                    isSslFilterCall = true;
                                }
                            }
                            
                            if (isSslFilterCall) {
                                System.out.println("Removing initiateHandshake call at: " + 
                                    javaFile + ":" + n.getRange().map(r -> r.begin.line).orElse(0));
                                n.remove();
                                fileModified.incrementAndGet();
                                transformations.incrementAndGet();
                            }
                        }
                    }
                }, null);
                
                // Pattern 4: Fix PEER_ADDRESS constant usage
                cu.accept(new VoidVisitorAdapter<Void>() {
                    @Override
                    public void visit(FieldAccessExpr n, Void arg) {
                        super.visit(n, arg);
                        
                        // Match SslFilter.PEER_ADDRESS
                        if (n.getNameAsString().equals("PEER_ADDRESS")) {
                            String scopeName = n.getScope().toString();
                            if (scopeName.equals("SslFilter") || scopeName.endsWith(".SslFilter")) {
                                System.out.println("Found PEER_ADDRESS usage at: " + 
                                    javaFile + ":" + n.getRange().map(r -> r.begin.line).orElse(0));
                                
                                // This needs manual inspection - suggest replacement
                                // For now, we'll just flag it
                            }
                        }
                    }
                }, null);
                
                // Pattern 5: Remove session.setAttribute(PEER_ADDRESS, ...) calls
                cu.accept(new VoidVisitorAdapter<Void>() {
                    @Override
                    public void visit(MethodCallExpr n, Void arg) {
                        super.visit(n, arg);
                        
                        // Match: session.setAttribute(PEER_ADDRESS, remoteAddress)
                        if (n.getNameAsString().equals("setAttribute") && 
                            n.getArguments().size() == 2) {
                            
                            String firstArg = n.getArgument(0).toString();
                            if (firstArg.contains("PEER_ADDRESS")) {
                                System.out.println("Removing setAttribute with PEER_ADDRESS at: " + 
                                    javaFile + ":" + n.getRange().map(r -> r.begin.line).orElse(0));
                                n.remove();
                                fileModified.incrementAndGet();
                                transformations.incrementAndGet();
                            }
                        }
                    }
                }, null);
                
                if (fileModified.get() > 0) {
                    try (FileWriter writer = new FileWriter(javaFile.toFile())) {
                        writer.write(cu.toString());
                    }
                    transformedFiles.incrementAndGet();
                }
                
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("\nTransformation complete!");
        System.out.println("Modified " + transformedFiles.get() + " files");
        System.out.println("Applied " + transformations.get() + " transformations");
    }
}