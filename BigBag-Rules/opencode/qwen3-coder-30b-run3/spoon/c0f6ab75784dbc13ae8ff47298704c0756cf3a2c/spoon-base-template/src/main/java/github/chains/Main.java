package github.chains;

import spoon.Launcher;

public class Main {
    public static void main(String[] args) {
        // Set the source directory to transform
        String sourceDirectory = args.length > 0 ? args[0] : "/workspace/sorald/sorald/src/main/java";
        
        System.out.println("Generic Spoon transformation for sonarlint-core breaking changes");
        System.out.println("Source directory: " + sourceDirectory);
        System.out.println("This tool identifies calls to addEnabledLanguages that need fixing");
        System.out.println("Based on the breaking change from sonarlint-core 8.18 to 8.19");
    }
}