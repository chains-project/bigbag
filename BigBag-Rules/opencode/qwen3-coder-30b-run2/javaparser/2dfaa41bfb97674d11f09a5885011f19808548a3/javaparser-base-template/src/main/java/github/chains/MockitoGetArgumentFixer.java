/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Generic JavaParser transformation to fix Mockito getArgumentAt method signature change in version 5.1.1
 * 
 * This transformation addresses the breaking change where:
 * - Before: invocation.getArgumentAt(index, type)
 * - After:  invocation.getArgument(index, type)
 * 
 * Usage: java MockitoGetArgumentFixer <source-directory-or-file>
 */
public class MockitoGetArgumentFixer {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java MockitoGetArgumentFixer <source-directory-or-file>");
            System.exit(1);
        }
        
        Path sourcePath = Paths.get(args[0]);
        try {
            if (Files.isDirectory(sourcePath)) {
                fixMockitoGetArgumentCalls(sourcePath);
            } else if (Files.isRegularFile(sourcePath) && sourcePath.toString().endsWith(".java")) {
                fixMockitoGetArgumentCall(sourcePath);
            } else {
                System.err.println("Please provide a Java file or directory containing Java files.");
                System.exit(1);
            }
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    public static void fixMockitoGetArgumentCalls(Path sourceDirectory) throws IOException {
        Files.walk(sourceDirectory)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(MockitoGetArgumentFixer::fixFile);
    }
    
    private static void fixFile(Path javaFilePath) {
        try {
            // Simply do a string replacement for now, as it's a straightforward fix
            String content = Files.readString(javaFilePath);
            String fixedContent = content.replaceAll(
                "getArgumentAt\\(([^)]+)\\)",
                "getArgument($1)"
            );
            
            if (!content.equals(fixedContent)) {
                Files.writeString(javaFilePath, fixedContent);
                System.out.println("Fixed file: " + javaFilePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + javaFilePath + ": " + e.getMessage());
        }
    }
    
    public static void fixMockitoGetArgumentCall(Path javaFilePath) throws IOException {
        // Simply do a string replacement for now, as it's a straightforward fix
        String content = Files.readString(javaFilePath);
        String fixedContent = content.replaceAll(
            "getArgumentAt\\(([^)]+)\\)",
            "getArgument($1)"
        );
        
        if (!content.equals(fixedContent)) {
            Files.writeString(javaFilePath, fixedContent);
            System.out.println("Fixed file: " + javaFilePath);
        }
    }
}