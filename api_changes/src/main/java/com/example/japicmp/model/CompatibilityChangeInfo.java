package com.example.japicmp.model;

public record CompatibilityChangeInfo(String type,
                                      boolean binaryCompatible,
                                      boolean sourceCompatible,
                                      String semanticVersionImpact) {
}

