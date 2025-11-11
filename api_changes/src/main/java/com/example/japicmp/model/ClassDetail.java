package com.example.japicmp.model;

import java.util.List;

public record ClassDetail(String classType,
                          ValueChange<String> superclass,
                          List<InterfaceChange> interfaces,
                          ValueChange<String> classFileVersion,
                          List<String> oldModifiers,
                          List<String> newModifiers,
                          List<AnnotationDetail> annotations,
                          List<MemberChange> constructors,
                          List<MemberChange> methods,
                          List<MemberChange> fields) {

    public ClassDetail {
        interfaces = List.copyOf(interfaces);
        oldModifiers = List.copyOf(oldModifiers);
        newModifiers = List.copyOf(newModifiers);
        annotations = List.copyOf(annotations);
        constructors = List.copyOf(constructors);
        methods = List.copyOf(methods);
        fields = List.copyOf(fields);
    }
}

