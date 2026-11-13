package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Generic, reusable transformation rule for the breaking dependency update of
 * org.apache.struts:struts2-core to version 2.5.22.
 *
 * <p>Characterization of the breaking change (abstract, dependency-side):
 * the dependency REMOVED/RELOCATED a package namespace. Every class that used to live under the
 * deprecated "org.apache.struts2.dispatcher.ng.*" namespace now lives at the identical sub-path of
 * "org.apache.struts2.dispatcher.*" — i.e. the ".ng" segment was dropped from the package prefix
 * while class names, members and signatures were kept unchanged.
 *
 * <p>Old API pattern at any client call site:
 * <pre>
 *   import org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter;
 *   org.apache.struts2.dispatcher.ng.&lt;sub&gt;.SomeClass          // qualified type usage
 *   org.apache.struts2.dispatcher.ng.&lt;sub&gt;.SomeClass.member    // qualified static access
 * </pre>
 *
 * <p>New API pattern (per the 2.5.22 exported API surface):
 * <pre>
 *   import org.apache.struts2.dispatcher.filter.StrutsPrepareAndExecuteFilter;
 *   org.apache.struts2.dispatcher.&lt;sub&gt;.SomeClass
 * </pre>
 *
 * <p>Structural transformation required: rewrite the PACKAGE PREFIX of every fully qualified type
 * reference rooted in a relocated dependency package, preserving the class/member suffix. Simple
 * (non-qualified) names resolved through the rewritten imports need no further change.
 *
 * <p>Nothing project-specific is hardcoded: the rule is parameterized ONLY by the dependency's
 * old/new fully-qualified package names (RELOCATED_PACKAGES below). Any Maven/Java project broken
 * by this dependency update is fixed by simply pointing this tool at its source directory:
 * <pre>  java github.chains.Main &lt;path-to-project-sources&gt;</pre>
 */
public class Main {

  // Dependency API parameters: {oldPackagePrefix, newPackagePrefix} pairs taken from the
  // struts2-core 2.5.22 exported API (NOT from any client project). The ".ng" namespace segment
  // was removed from the package hierarchy, so any reference rooted under ".ng" is relocated by
  // dropping that segment (e.g. org.apache.struts2.dispatcher.ng.filter.X ->
  // org.apache.struts2.dispatcher.filter.X).
  private static final String[][] RELOCATED_PACKAGES = {
    {"org.apache.struts2.dispatcher.ng", "org.apache.struts2.dispatcher"},
  };

  public static void main(String[] args) throws IOException {
    Path root =
        args.length > 0 ? Paths.get(args[0]) : Paths.get(System.getProperty("project.dir", "."));
    List<Path> javaFiles = new ArrayList<>();
    try (Stream<Path> walk = Files.walk(root)) {
      walk.filter(p -> p.toString().endsWith(".java"))
          .filter(Files::isRegularFile)
          .filter(p -> !p.toString().contains("/target/"))
          .forEach(javaFiles::add);
    }
    int changed = 0;
    int failed = 0;
    for (Path file : javaFiles) {
      try {
        if (transformFile(file)) {
          changed++;
          System.out.println("TRANSFORMED: " + file);
        }
      } catch (Exception e) {
        failed++;
        System.err.println("SKIPPED (parse/IO error): " + file + " -> " + e.getMessage());
      }
    }
    System.out.println(
        "Scanned " + javaFiles.size() + " file(s); transformed " + changed + "; errors " + failed);
  }

  /** Parses one compilation unit, applies the rule, rewrites the file in place if it matched. */
  private static boolean transformFile(Path file) throws IOException {
    JavaParser parser =
        new JavaParser(
            new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17)
                .setLexicalPreservationEnabled(true));
    ParseResult<CompilationUnit> result = parser.parse(file);
    if (!result.isSuccessful() || !result.getResult().isPresent()) {
      throw new IOException("could not parse: " + result.getProblems());
    }
    CompilationUnit cu = result.getResult().get();
    if (!applyRule(cu)) {
      return false;
    }
    Files.write(file, cu.toString().getBytes(StandardCharsets.UTF_8));
    return true;
  }

  /**
   * The generic rule: every fully qualified reference rooted in one of the relocated dependency
   * packages has its package prefix rewritten, class/member suffix preserved. Traversal covers all
   * AST shapes a qualified type reference can take.
   */
  private static boolean applyRule(CompilationUnit cu) {
    boolean changed = false;

    // 1) Import declarations (normal and static, single-type and on-demand).
    for (ImportDeclaration imp : cu.findAll(ImportDeclaration.class)) {
      String relocated = relocate(imp.getNameAsString());
      if (relocated != null) {
        imp.setName(StaticJavaParser.parseName(relocated));
        changed = true;
      }
    }

    // 2) Class/interface type usages (variables, casts, instanceof, .class literals, generics...).
    //    A fully qualified type is stored as a nested ClassOrInterfaceType scope chain, so the full
    //    dotted name is reconstructed from the outermost node; it is then replaced with a freshly
    //    parsed type carrying the relocated package prefix.
    for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
      if (isScopeOf(type)) {
        continue; // handled at the outermost node of the chain
      }
      String relocated = relocate(fullTypeName(type));
      if (relocated != null) {
        type.replace(StaticJavaParser.parseClassOrInterfaceType(relocated));
        changed = true;
      }
    }

    // 3) Any other fully qualified Name node (e.g. qualified annotation names), handled
    //    generically by name-string prefix match. Import names are already fixed in step 1.
    List<Name> names = cu.findAll(Name.class);
    for (Name n : names) {
      if (n.getParentNode().filter(p -> p instanceof ImportDeclaration).isPresent()) {
        continue;
      }
      String relocated = relocate(n.asString());
      if (relocated != null && n.getParentNode().isPresent()) {
        n.replace(StaticJavaParser.parseName(relocated));
        changed = true;
      }
    }

    // 4) Qualified-name EXPRESSION chains (e.g. org.apache.struts2.dispatcher.ng.filter.X.member),
    //    which JavaParser represents as nested NameExpr/FieldAccessExpr scopes.
    Set<Expression> chainRoots = new LinkedHashSet<>();
    for (NameExpr ne : cu.findAll(NameExpr.class)) {
      chainRoots.add(topOfChain(ne));
    }
    for (FieldAccessExpr fae : cu.findAll(FieldAccessExpr.class)) {
      chainRoots.add(topOfChain(fae));
    }
    for (MethodCallExpr mc : cu.findAll(MethodCallExpr.class)) {
      mc.getScope().ifPresent(scope -> chainRoots.add(topOfChain(scope)));
    }
    for (Expression root : chainRoots) {
      String relocated = relocate(chainString(root));
      if (relocated != null) {
        root.replace(buildChain(relocated));
        changed = true;
      }
    }

    return changed;
  }

  /** True if this type node is the scope of another ClassOrInterfaceType (i.e. not outermost). */
  private static boolean isScopeOf(ClassOrInterfaceType type) {
    Node parent = type.getParentNode().orElse(null);
    return parent instanceof ClassOrInterfaceType
        && ((ClassOrInterfaceType) parent).getScope().orElse(null) == type;
  }

  /** Reconstructs the full dotted name of a ClassOrInterfaceType including its scope chain. */
  private static String fullTypeName(ClassOrInterfaceType type) {
    String leaf = type.getNameAsString();
    ClassOrInterfaceType scope = type.getScope().orElse(null);
    return scope == null ? leaf : fullTypeName(scope) + "." + leaf;
  }

  /** Maps a fully qualified name rooted in a relocated dependency package to its new name. */
  private static String relocate(String qualifiedName) {    if (qualifiedName == null) {
      return null;
    }
    for (String[] mapping : RELOCATED_PACKAGES) {
      String oldPrefix = mapping[0];
      if (qualifiedName.equals(oldPrefix) || qualifiedName.startsWith(oldPrefix + ".")) {
        return mapping[1] + qualifiedName.substring(oldPrefix.length());
      }
    }
    return null;
  }

  /** Climbs from a scope node to the root of its pure dotted-name chain. */
  private static Expression topOfChain(Expression e) {
    Node parent = e.getParentNode().orElse(null);
    if (parent instanceof FieldAccessExpr && ((FieldAccessExpr) parent).getScope() == e) {
      return topOfChain((FieldAccessExpr) parent);
    }
    return e;
  }

  /** Flattens a NameExpr/FieldAccessExpr scope chain into a dotted string; null if impure. */
  private static String chainString(Expression e) {
    if (e instanceof NameExpr) {
      return ((NameExpr) e).getNameAsString();
    }
    if (e instanceof FieldAccessExpr) {
      FieldAccessExpr fae = (FieldAccessExpr) e;
      String scope = chainString(fae.getScope());
      return scope == null ? null : scope + "." + fae.getNameAsString();
    }
    return null;
  }

  /** Rebuilds a dotted qualified name as a nested NameExpr/FieldAccessExpr chain. */
  private static Expression buildChain(String qualifiedName) {
    String[] parts = qualifiedName.split("\\.");
    Expression expr = new NameExpr(parts[0]);
    for (int i = 1; i < parts.length; i++) {
      expr = new FieldAccessExpr(expr, parts[i]);
    }
    return expr;
  }
}
