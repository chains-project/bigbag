package github.chains.core;

import github.chains.core.cli.MainCli;

/**
 * Main entry point for the breaking update processor.
 * Delegates to MainCli for command-line processing.
 */
public class Main {

    public static void main(String[] args) {
        MainCli.main(args);
    }
}
