package github.chains;

public class Main {
    public static void main(String[] args) {
        System.out.println("Spoon transformation tool for Jakarta MVC migration");
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source-directory>");
            System.exit(1);
        }
        System.out.println("Transforming directory: " + args[0]);
    }
}