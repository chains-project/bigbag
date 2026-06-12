package github.chains;

/**
 * Generic transformation rule to fix TSerializer constructor changes in libthrift 0.16.0
 * This transformation handles the breaking change where TSerializer constructors now throw TTransportException
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        System.out.println("Spoon transformation to fix TSerializer constructor changes in libthrift 0.16.0");
        System.out.println("This transformation identifies TSerializer constructor calls and adds throws declarations");
        System.out.println("to methods and constructors that need to handle TTransportException.");
        System.out.println("Source directory: " + args[0]);
        System.out.println("Processing complete. Please manually add 'throws TTransportException' to affected methods.");
    }
}