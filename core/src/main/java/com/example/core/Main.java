package com.example.core;

import com.example.core.cli.MainCli;

/**
 * Main entry point for the breaking update processor.
 * Delegates to MainCli for command-line processing.
 */
public class Main {

    public static void main(String[] args) {
        MainCli.main(args);
    }
}
