package github.chains;

public class Main {
    public static void main(String[] args) {
        System.out.println("Flyway API Fix Transformer");
        System.out.println("This tool transforms Flyway constructor calls from new Flyway() to new Flyway(Flyway.configure())");
        System.out.println("to fix breaking changes in Flyway API versions.");
    }
}
