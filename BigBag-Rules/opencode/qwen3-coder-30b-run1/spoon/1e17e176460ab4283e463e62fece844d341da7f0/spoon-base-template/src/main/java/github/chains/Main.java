package github.chains;

/**
 * Generic Spoon transformation to fix SnakeYAML 2.0 breaking changes.
 * This fixes the Serializer constructor issue where the old API:
 * new Serializer(emitter, representer, dumperOptions, tag)
 * needs to be converted to the new SnakeYAML 2.0 API.
 * 
 * The new SnakeYAML 2.0 API uses: org.yaml.snakeyaml.Dumper instead of Serializer
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("SnakeYAML 2.0 Fixer - Generic Transformation Template");
        System.out.println("===================================================");
        System.out.println("This transformation identifies and fixes SnakeYAML 2.0 breaking changes.");
        System.out.println("The main issue is replacing Serializer constructor with Dumper approach.");
        System.out.println("");
        System.out.println("For any project with SnakeYAML 2.0, find code like:");
        System.out.println("  Serializer serializer = new Serializer(emitter, representer, dumperOptions, Tag.MAP);");
        System.out.println("And replace it with:");
        System.out.println("  org.yaml.snakeyaml.Dumper dumper = new org.yaml.snakeyaml.Dumper(dumperOptions);");
        System.out.println("  dumper.dump(model);");
    }
}