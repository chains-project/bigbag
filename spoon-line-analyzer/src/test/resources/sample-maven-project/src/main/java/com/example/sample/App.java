package com.example.sample;

import java.util.List;

public class App {

    private final Helper helper = new Helper();

    public List<String> run() {
        List<String> result = helper.provide();
        return result;
    }
}

