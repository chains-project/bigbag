package github.chains;

/**
 * Generic transformation to fix JsonBranch -> DataElement migration issues.
 * This fixes the breaking change where JsonBranch was replaced with DataElement
 * in de.gwdg.metadataqa.api.json package.
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("Spoon transformation tool ready");
        System.out.println("Usage: java -cp target/spoon-transformer-1.0-SNAPSHOT.jar github.chains.Main /path/to/source");
    }
}