package github.chains;

/**
 * Generic transformation rule for fixing the Maven31DependencyGraphBuilder breaking change.
 * 
 * Breaking Change: Maven31DependencyGraphBuilder was removed in maven-dependency-tree 3.2.1
 * 
 * Old API pattern: new Maven31DependencyGraphBuilder() (no-argument constructor)
 * New API pattern: Cannot create directly, requires dependency injection
 * 
 * Transformation rules:
 * 1. Remove imports of org.apache.maven.shared.dependency.graph.internal.Maven31DependencyGraphBuilder
 * 2. For patterns like Optional.ofNullable(x).orElse(new Maven31DependencyGraphBuilder()):
 *    - Replace with just 'x' (removes the default)
 * 3. For standalone new Maven31DependencyGraphBuilder():
 *    - Replace with 'null'
 *    
 * This transformation is generic and can be applied to any project affected by this breaking change.
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("Maven31DependencyGraphBuilder Transformation Rule");
        System.out.println("==================================================");
        System.out.println();
        System.out.println("BREAKING CHANGE: Maven31DependencyGraphBuilder was removed in maven-dependency-tree 3.2.1");
        System.out.println();
        System.out.println("OLD API PATTERN:");
        System.out.println("  - new Maven31DependencyGraphBuilder()");
        System.out.println("  - Optional.ofNullable(graph).orElse(new Maven31DependencyGraphBuilder())");
        System.out.println();
        System.out.println("NEW API PATTERN:");
        System.out.println("  - DefaultDependencyGraphBuilder requires ProjectDependenciesResolver parameter");
        System.out.println("  - Cannot create directly, must use dependency injection");
        System.out.println();
        System.out.println("TRANSFORMATION RULES:");
        System.out.println("1. Remove imports of Maven31DependencyGraphBuilder");
        System.out.println("2. Replace Optional.ofNullable(x).orElse(new Maven31DependencyGraphBuilder()) with 'x'");
        System.out.println("3. Replace standalone new Maven31DependencyGraphBuilder() with 'null'");
        System.out.println();
        System.out.println("EXAMPLE:");
        System.out.println("  Before: this.graph = Optional.ofNullable(graph).orElse(new Maven31DependencyGraphBuilder())");
        System.out.println("  After:  this.graph = graph");
        System.out.println();
        System.out.println("  Before: DependencyGraphBuilder builder = new Maven31DependencyGraphBuilder()");
        System.out.println("  After:  DependencyGraphBuilder builder = null");
        System.out.println();
        System.out.println("This transformation preserves compilation but may change runtime behavior");
        System.out.println("if the default constructor was actually used.");
    }
}