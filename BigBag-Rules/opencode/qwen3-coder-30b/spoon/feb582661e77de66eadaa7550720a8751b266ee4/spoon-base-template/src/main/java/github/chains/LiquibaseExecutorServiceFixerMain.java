package github.chains;

import spoon.Launcher;

public class LiquibaseExecutorServiceFixerMain {
    public static void main(String[] args) {
        System.out.println("Liquibase ExecutorService API Fixer");
        System.out.println("Usage: java LiquibaseExecutorServiceFixerMain <source-directory> [output-directory]");
        System.out.println("");
        System.out.println("This tool fixes the ExecutorService API change in Liquibase 4.8.0 projects.");
        System.out.println("It transforms calls from:");
        System.out.println("  ExecutorService.getInstance().getExecutor(database).execute(statement)");
        System.out.println("To:");
        System.out.println("  ExecutorService.getInstance().execute(database, statement)");
        System.out.println("");
        System.out.println("The transformation is generic and works with any project using this API pattern.");
    }
}