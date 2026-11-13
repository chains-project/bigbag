package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import spoon.Launcher;
import spoon.reflect.code.CtCodeSnippetExpression;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Filter;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * GENERIC Spoon transformation fixing the breaking change introduced by the Jakarta
 * Bean Validation namespace migration (dependency jakarta.validation:jakarta.validation-api,
 * upgrade 2.x -> 3.x, e.g. 2.0.2 -> 3.0.2).
 *
 * <p>Breaking change (abstract characterization):
 * <ul>
 *   <li>OLD API pattern : any client-side type usage whose fully qualified name matches
 *       {@code javax.validation.**} (imports, declared types of fields/locals/parameters,
 *       annotation usages, {@code instanceof} targets, generic type arguments), plus
 *       Validator instances acquired from an expression whose static type is the
 *       old-namespace interface {@code javax.validation.ValidatorFactory}.</li>
 *   <li>NEW API pattern : the same classes/methods, moved to the root package
 *       {@code jakarta.validation.**} (identical simple names and sub-packages:
 *       {@code Validator}, {@code ValidatorFactory}, {@code constraints.*},
 *       {@code metadata.*}, ...). The only entry point for a factory instance in the new
 *       namespace is the static method {@code jakarta.validation.Validation.buildDefaultValidatorFactory()}.</li>
 *   <li>Structural transformation at call sites:
 *       (1) rewrite the package prefix of every affected type reference;
 *       (2) a factory instance of the old namespace leaks from third-party binaries compiled
 *           against 2.x (their members still carry {@code javax.validation.ValidatorFactory} signatures),
 *           so any {@code getValidator()} call whose receiver is statically typed as the old
 *           {@code javax.validation.ValidatorFactory} can never produce a new-namespace
 *           {@code Validator} - the receiver must be replaced by the new-namespace factory
 *           acquisition expression {@code Validation.buildDefaultValidatorFactory()}.</li>
 * </ul>
 *
 * <p>Everything below is parameterized ONLY by the dependency's fully qualified names; it is
 * applicable to ANY Maven project by changing the input source directory (and optionally the
 * project classpath). No client project identifiers appear in this rule.
 *
 * <p>Usage: {@code java github.chains.Main <sourceDirectory> [classpathStringOrClasspathFile]}
 */
public class Main {

	// ==== Dependency API parameters (fully-qualified names of the dependency, NOT of the client) ====

	/** Root package of the Bean Validation API before the update (types removed by the update). */
	private static final String OLD_ROOT_PACKAGE = "javax.validation";
	/** Root package of the Bean Validation API after the update (same types, new namespace). */
	private static final String NEW_ROOT_PACKAGE = "jakarta.validation";

	/** Old-namespace factory type that may leak from binaries compiled against the previous version. */
	private static final String OLD_FACTORY_TYPE = OLD_ROOT_PACKAGE + ".ValidatorFactory";
	/** New-namespace factory type produced by the updated dependency. */
	private static final String NEW_FACTORY_TYPE = NEW_ROOT_PACKAGE + ".ValidatorFactory";
	/** Method invoked on a factory to obtain a Validator (unchanged name/params across versions). */
	private static final String VALIDATOR_ACQUISITION_METHOD = "getValidator";
	/** New-namespace static entry point returning a factory instance. */
	private static final String NEW_ENTRY_POINT_TYPE = NEW_ROOT_PACKAGE + ".Validation";
	private static final String NEW_ENTRY_POINT_METHOD = "buildDefaultValidatorFactory";

	// ==== Generic entry point ====

	public static void main(String[] args) throws IOException {
		String inputSourceDir = args.length > 0 ? args[0] : System.getProperty("user.dir") + "/src";
		String[] classpath = args.length > 1 ? readClasspath(args[1]) : new String[0];
		Path outputDir = args.length > 2
				? Paths.get(args[2])
				: Files.createTempDirectory("spoon-namespace-migration");

		Launcher launcher = new Launcher();
		launcher.addInputResource(inputSourceDir);
		launcher.getEnvironment().setNoClasspath(true);
		launcher.getEnvironment().setCommentEnabled(true);
		launcher.getEnvironment().setAutoImports(true);
		launcher.getEnvironment().setComplianceLevel(17);
		if (classpath.length > 0) {
			launcher.getEnvironment().setSourceClasspath(classpath);
		}
		launcher.buildModel();

		int factoriesRewritten = rewriteOldNamespaceFactoryAcquisition(launcher);
		int referencesRenamed = renameOldNamespaceTypeReferences(launcher);

		System.out.println("[transformation] factory acquisition sites rewritten : " + factoriesRewritten);
		System.out.println("[transformation] type references migrated to new ns  : " + referencesRenamed);

		launcher.setSourceOutputDirectory(outputDir.toFile());
		launcher.prettyprint();
		int filesUpdated = copyBackChangedFiles(outputDir, Paths.get(inputSourceDir));
		System.out.println("[transformation] source files updated in place       : " + filesUpdated);
	}

	// ==== Rule 1: replace acquisition of a Validator from an old-namespace ValidatorFactory ====

	/**
	 * Matches structurally: {@code <receiver>.getValidator()} where the receiver is statically typed
	 * as the dependency's OLD factory type {@code javax.validation.ValidatorFactory} (no arguments).
	 * Such a receiver is supplied by third-party binaries still exposing the removed namespace, so the
	 * value can never satisfy the new API; the receiver is replaced by the new-namespace entry point
	 * {@code jakarta.validation.Validation.buildDefaultValidatorFactory()}.
	 */
	private static int rewriteOldNamespaceFactoryAcquisition(Launcher launcher) {
		List<CtInvocation<?>> invocations = launcher.getModel()
				.getElements(new TypeFilter<>(CtInvocation.class));
		int count = 0;
		for (CtInvocation<?> invocation : invocations) {
			if (!VALIDATOR_ACQUISITION_METHOD.equals(invocation.getExecutable().getSimpleName())
					|| !invocation.getArguments().isEmpty()) {
				continue;
			}
			CtExpression<?> target = invocation.getTarget();
			if (target == null) {
				continue; // static call of the client itself: not an instance acquisition
			}
			CtTypeReference<?> targetType = target.getType();
			String targetName = targetType == null ? "" : targetType.getQualifiedName();
			if (OLD_FACTORY_TYPE.equals(targetName) || NEW_FACTORY_TYPE.equals(targetName)) {
				CtCodeSnippetExpression<?> replacement = launcher.getFactory().Code()
						.createCodeSnippetExpression(
								NEW_ENTRY_POINT_TYPE + "." + NEW_ENTRY_POINT_METHOD + "()");
				replacement.setType(launcher.getFactory().Type().createReference(NEW_FACTORY_TYPE));
				invocation.setTarget(replacement);
				count++;
			}
		}
		return count;
	}

	// ==== Rule 2: migrate every client type reference javax.validation.** -> jakarta.validation.** ====

	/**
	 * Matches structurally: every type reference declared in the analyzed sources (valid position)
	 * whose fully qualified name starts with the dependency's old root package. Replacement keeps
	 * generic type arguments and is applied wherever the old pattern appears in any traversed file
	 * (imports, field/local/parameter/catch types, annotations, instanceof, casts, generics).
	 */
	private static int renameOldNamespaceTypeReferences(Launcher launcher) {
		int migrated = 0;
		List<CtTypeReference<?>> references = launcher.getModel()
				.getElements((Filter<CtTypeReference<?>>) element -> {
					if (!(element instanceof CtTypeReference)) {
						return false;
					}
					CtTypeReference<?> ref = (CtTypeReference<?>) element;
					boolean matches = startsWithOldRootPackage(ref.getQualifiedName());
					return matches;
				});
		for (CtTypeReference<?> reference : new ArrayList<>(references)) {
			if (!reference.isParentInitialized()) {
				continue;
			}
			// Skip references with no source position (e.g., inferred types).
			if (!reference.getPosition().isValidPosition()) {
				continue;
			}
			// Already migrated?
			if (reference.getQualifiedName().startsWith(NEW_ROOT_PACKAGE)) {
				continue;
			}
			try {
				CtElement parent = reference.getParent();
				if (parent instanceof CtImport) {
					((CtImport) parent).setReference(migratedReference(launcher, reference));
					migrated++;
					continue;
				}
				reference.replace(migratedReference(launcher, reference));
				migrated++;
			} catch (RuntimeException e) {
				// replace failed
				System.err.println("[transformation] failed to replace " + reference + ": " + e);
			}
		}
		return migrated;
	}

	private static void migrateReference(Launcher launcher, CtTypeReference<?> reference, int[] count) {
		try {
			reference.replace(migratedReference(launcher, reference));
			count[0]++;
		} catch (RuntimeException e) {
			// Parent role not replaceable; if it is an import handle it through its owner below.
			CtElement parent = reference.getParent();
			if (parent instanceof CtImport) {
				((CtImport) parent).setReference(migratedReference(launcher, reference));
				count[0]++;
			}
		}
	}

	/** Builds the new-namespace equivalent of an old-namespace type reference (same generics). */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static CtTypeReference<?> migratedReference(Launcher launcher, CtTypeReference<?> old) {
		if (old instanceof CtArrayTypeReference) {
			CtTypeReference<?> component =
					migratedReference(launcher, ((CtArrayTypeReference<?>) old).getComponentType());
			CtArrayTypeReference migratedArray = launcher.getFactory().Core().createArrayTypeReference();
			migratedArray.setComponentType(component);
			return migratedArray;
		}
		String qualifiedName = old.getQualifiedName();
		String migrated = NEW_ROOT_PACKAGE + qualifiedName.substring(OLD_ROOT_PACKAGE.length());
		CtTypeReference migratedReference = launcher.getFactory().Type().createReference(migrated);
		if (!old.getActualTypeArguments().isEmpty()) {
			migratedReference.setActualTypeArguments(new ArrayList<>(old.getActualTypeArguments()));
		}
		return migratedReference;
	}

	private static boolean startsWithOldRootPackage(String qualifiedName) {
		return qualifiedName.equals(OLD_ROOT_PACKAGE)
				|| qualifiedName.startsWith(OLD_ROOT_PACKAGE + ".");
	}

	// ==== In-place output ====

	/** Copies pretty-printed files back over the originals, only when their content changed. */
	private static int copyBackChangedFiles(Path printedTree, Path inputRoot) throws IOException {
		Set<String> relativePaths = new LinkedHashSet<>();
		try (Stream<Path> walk = Files.walk(printedTree)) {
			walk.filter(p -> p.toString().endsWith(".java")).forEach(p ->
					relativePaths.add(printedTree.relativize(p).toString()));
		}
		int updated = 0;
		for (String relativePath : relativePaths) {
			List<Path> printed = new ArrayList<>();
			printed.add(printedTree.resolve(relativePath));
			List<Path> originals = new ArrayList<>();
			try (Stream<Path> walk = Files.walk(inputRoot)) {
				walk.filter(p -> p.toString().endsWith(File.separator + relativePath)
								|| p.getFileName().toString().endsWith(relativePath))
					.forEach(originals::add);
			}
			if (originals.isEmpty()) {
				continue;
			}
			String newContent = Files.readString(printed.get(0));
			for (Path original : originals) {
				if (!Files.readString(original).equals(newContent)) {
					Files.writeString(original, newContent);
					updated++;
					System.out.println("[transformation] migrated: " + original);
				}
			}
		}
		return updated;
	}

	private static String[] readClasspath(String spec) throws IOException {
		File maybeFile = new File(spec);
		String raw = spec;
		if (maybeFile.isFile()) {
			raw = Files.readString(maybeFile.toPath()).trim();
		}
		if (raw.isEmpty()) {
			return new String[0];
		}
		return raw.split(File.pathSeparator);
	}
}
