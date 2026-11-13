package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp target/classes github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = findJavaFiles(Paths.get(sourceDir));
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            int transformedFiles = 0;
            int totalTransformations = 0;
            
            for (Path javaFile : javaFiles) {
                TransformationResult result = processFile(javaFile);
                if (result.transformations > 0) {
                    transformedFiles++;
                    totalTransformations += result.transformations;
                    System.out.println("Transformed " + result.transformations + " Constructor(s) in " + javaFile);
                }
            }
            
            System.out.println("Summary: Transformed " + totalTransformations + " Constructor calls in " + transformedFiles + " files");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(Path dir) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        return javaFiles;
    }
    
    private static TransformationResult processFile(Path javaFile) throws IOException {
        String content = Files.readString(javaFile);
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(content).getResult().orElse(null);
        
        if (cu == null) {
            return new TransformationResult(false, 0);
        }
        
        ConstructorTransformer transformer = new ConstructorTransformer(cu);
        transformer.visit(cu, null);
        
        if (transformer.getTransformations() > 0) {
            String newContent = cu.toString();
            Files.write(javaFile, newContent.getBytes());
            return new TransformationResult(true, transformer.getTransformations());
        }
        
        return new TransformationResult(false, 0);
    }
    
    private static class TransformationResult {
        boolean changed;
        int transformations;
        
        TransformationResult(boolean changed, int transformations) {
            this.changed = changed;
            this.transformations = transformations;
        }
    }
    
    private static class ConstructorTransformer extends VoidVisitorAdapter<Void> {
        private final CompilationUnit cu;
        private int transformations = 0;
        private boolean addedLoaderOptionsImport = false;
        
        ConstructorTransformer(CompilationUnit cu) {
            this.cu = cu;
        }
        
        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            
            String typeName = n.getType().getNameAsString();
            
            // Check if this is a snakeyaml Constructor
            boolean isSnakeYamlConstructor = false;
            
            // Check if type name is "Constructor"
            if (typeName.equals("Constructor")) {
                // Check if it's qualified with scope
                if (n.getScope().isPresent()) {
                    String scopeName = n.getScope().get().toString();
                    if (scopeName.equals("org.yaml.snakeyaml.constructor")) {
                        isSnakeYamlConstructor = true;
                    }
                } else {
                    // Check imports to see if it's org.yaml.snakeyaml.constructor.Constructor
                    boolean hasConstructorImport = false;
                    for (ImportDeclaration importDecl : cu.getImports()) {
                        String importName = importDecl.getNameAsString();
                        if (importName.equals("org.yaml.snakeyaml.constructor.Constructor") ||
                            importName.equals("org.yaml.snakeyaml.Constructor")) {
                            hasConstructorImport = true;
                            break;
                        }
                    }
                    // Also check for wildcard imports
                    for (ImportDeclaration importDecl : cu.getImports()) {
                        if (importDecl.isAsterisk()) {
                            String importName = importDecl.getNameAsString();
                            if (importName.equals("org.yaml.snakeyaml.constructor") ||
                                importName.equals("org.yaml.snakeyaml")) {
                                hasConstructorImport = true;
                                break;
                            }
                        }
                    }
                    isSnakeYamlConstructor = hasConstructorImport;
                }
            }
            
            if (!isSnakeYamlConstructor) {
                return;
            }
            
            NodeList<Expression> arguments = n.getArguments();
            
            // Pattern 1: Constructor() -> Constructor(new LoaderOptions())
            // Pattern 2: Constructor(Class) -> Constructor(Class, new LoaderOptions())
            // Pattern 3: Constructor(String) -> Constructor(String, new LoaderOptions())
            
            // We need to transform if:
            // 1. No arguments (old no-arg constructor)
            // 2. One argument that is NOT a LoaderOptions creation
            
            if (arguments.isEmpty()) {
                // Pattern 1
                transformNoArgConstructor(n);
                transformations++;
            } else if (arguments.size() == 1) {
                Expression firstArg = arguments.get(0);
                // Check if this is already a LoaderOptions creation
                if (!isLoaderOptionsCreation(firstArg)) {
                    // Pattern 2 or 3
                    transformSingleArgConstructor(n, arguments);
                    transformations++;
                }
            }
            // Note: We don't handle multi-argument old constructors as they're less common
        }
        
        private boolean isLoaderOptionsCreation(Expression expr) {
            if (expr instanceof ObjectCreationExpr) {
                ObjectCreationExpr creation = (ObjectCreationExpr) expr;
                String typeName = creation.getType().getNameAsString();
                return typeName.equals("LoaderOptions");
            }
            return false;
        }
        
        private void transformNoArgConstructor(ObjectCreationExpr n) {
            // Create new LoaderOptions()
            ObjectCreationExpr loaderOptions = new ObjectCreationExpr();
            loaderOptions.setType("LoaderOptions");
            
            NodeList<Expression> newArgs = new NodeList<>();
            newArgs.add(loaderOptions);
            n.setArguments(newArgs);
            
            ensureLoaderOptionsImport();
        }
        
        private void transformSingleArgConstructor(ObjectCreationExpr n, NodeList<Expression> originalArgs) {
            // Create new LoaderOptions()
            ObjectCreationExpr loaderOptions = new ObjectCreationExpr();
            loaderOptions.setType("LoaderOptions");
            
            NodeList<Expression> newArgs = new NodeList<>();
            // Keep original argument as first
            newArgs.add(originalArgs.get(0));
            // Add LoaderOptions as second
            newArgs.add(loaderOptions);
            n.setArguments(newArgs);
            
            ensureLoaderOptionsImport();
        }
        
        private void ensureLoaderOptionsImport() {
            if (addedLoaderOptionsImport) {
                return;
            }
            
            // Check if LoaderOptions is already imported
            boolean hasLoaderOptionsImport = false;
            for (ImportDeclaration importDecl : cu.getImports()) {
                String importName = importDecl.getNameAsString();
                if (importName.equals("org.yaml.snakeyaml.LoaderOptions")) {
                    hasLoaderOptionsImport = true;
                    break;
                }
            }
            
            if (!hasLoaderOptionsImport) {
                cu.addImport("org.yaml.snakeyaml.LoaderOptions");
                addedLoaderOptionsImport = true;
            }
        }
        
        public int getTransformations() {
            return transformations;
        }
    }
}