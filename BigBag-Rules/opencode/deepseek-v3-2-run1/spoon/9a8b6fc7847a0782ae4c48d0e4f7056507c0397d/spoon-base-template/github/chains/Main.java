package github.chains;
public class Main {
    public static void main(java.lang.String[] args) {
        if (args.length < 1) {
            java.lang.System.err.println("Usage: java Main <source-directory>");
            java.lang.System.exit(1);
        }
        java.lang.String sourceDir = args[0];
        java.lang.System.out.println("Transforming source directory: " + sourceDir);
        // Create Spoon launcher
        spoon.Launcher launcher = new spoon.Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(sourceDir);
        // Build model
        spoon.reflect.CtModel model = launcher.buildModel();
        spoon.reflect.factory.Factory factory = launcher.getFactory();
        // Find all classes in the model
        java.util.List<spoon.reflect.declaration.CtType<?>> types = new java.util.ArrayList<>(model.getAllTypes());
        int transformCount = 0;
        for (spoon.reflect.declaration.CtType<?> type : types) {
            if (type instanceof spoon.reflect.declaration.CtClass) {
                spoon.reflect.declaration.CtClass<?> clazz = ((spoon.reflect.declaration.CtClass<?>) (type));
                boolean modified = false;
                // Get the compilation unit for import management
                spoon.reflect.declaration.CtCompilationUnit compUnit = type.getPosition().getCompilationUnit();
                if (compUnit != null) {
                    // Get all imports
                    java.util.List<spoon.reflect.declaration.CtImport> imports = new java.util.ArrayList<>(compUnit.getImports());
                    java.util.List<spoon.reflect.declaration.CtImport> newImports = new java.util.ArrayList<>();
                    boolean needsAcegiImport = false;
                    for (spoon.reflect.declaration.CtImport imp : imports) {
                        java.lang.String importStr = imp.toString();
                        if (importStr.contains("org.jasypt.spring.security.PasswordEncoder") || importStr.contains("org.jasypt.spring.security.PBEPasswordEncoder")) {
                            // Skip this import (don't add it to new list)
                            modified = true;
                            java.lang.System.out.println((("Removing jasypt import: " + importStr) + " in ") + clazz.getQualifiedName());
                            needsAcegiImport = true;
                        } else {
                            // Keep this import
                            newImports.add(imp);
                        }
                    }
                    // Add Acegi Security import if needed
                    if (needsAcegiImport) {
                        try {
                            spoon.reflect.reference.CtTypeReference<?> acegiTypeRef = factory.createReference("org.acegisecurity.providers.encoding.PasswordEncoder");
                            spoon.reflect.declaration.CtImport acegiImport = factory.createImport(acegiTypeRef);
                            newImports.add(acegiImport);
                            java.lang.System.out.println("Added import: org.acegisecurity.providers.encoding.PasswordEncoder in " + clazz.getQualifiedName());
                        } catch (java.lang.Exception e) {
                            java.lang.System.err.println("Failed to add Acegi import: " + e.getMessage());
                        }
                    }
                    // Replace imports in compilation unit
                    compUnit.setImports(newImports);
                }
                // Find constructor calls to jasypt spring security classes
                java.util.List<spoon.reflect.code.CtConstructorCall<?>> constructorCalls = spoon.reflect.visitor.Query.getElements(clazz, new spoon.reflect.visitor.filter.TypeFilter<spoon.reflect.code.CtConstructorCall<?>>(spoon.reflect.code.CtConstructorCall.class) {
                    @java.lang.Override
                    public boolean matches(spoon.reflect.code.CtConstructorCall<?> constructorCall) {
                        spoon.reflect.reference.CtTypeReference<?> typeRef = constructorCall.getType();
                        if (typeRef != null) {
                            java.lang.String typeName = typeRef.getQualifiedName();
                            return "org.jasypt.spring.security.PasswordEncoder".equals(typeName) || "org.jasypt.spring.security.PBEPasswordEncoder".equals(typeName);
                        }
                        return false;
                    }
                });
                for (spoon.reflect.code.CtConstructorCall<?> constructorCall : constructorCalls) {
                    spoon.reflect.reference.CtTypeReference<?> typeRef = constructorCall.getType();
                    java.lang.String typeName = typeRef.getQualifiedName();
                    // Get the parent statement to check if there are method calls on this constructor
                    spoon.reflect.code.CtStatement parentStatement = constructorCall.getParent(spoon.reflect.code.CtStatement.class);
                    java.lang.String replacementCode = "";
                    if ("org.jasypt.spring.security.PasswordEncoder".equals(typeName)) {
                        // Check if there's a setPasswordEncryptor call
                        boolean hasSetPasswordEncryptor = false;
                        if (parentStatement != null) {
                            java.util.List<spoon.reflect.code.CtInvocation<?>> invocations = spoon.reflect.visitor.Query.getElements(parentStatement, new spoon.reflect.visitor.filter.TypeFilter<spoon.reflect.code.CtInvocation<?>>(spoon.reflect.code.CtInvocation.class) {
                                @java.lang.Override
                                public boolean matches(spoon.reflect.code.CtInvocation<?> invocation) {
                                    return (invocation.getExecutable() != null) && invocation.getExecutable().getSimpleName().equals("setPasswordEncryptor");
                                }
                            });
                            hasSetPasswordEncryptor = !invocations.isEmpty();
                        }
                        // Create an anonymous inner class that implements PasswordEncoder
                        // This replaces: new PasswordEncoder()
                        // With an implementation that delegates to StrongPasswordEncryptor
                        java.lang.String adapterCode = ((((((((((((((((((((((("new org.acegisecurity.providers.encoding.PasswordEncoder() {\n" + "    private org.jasypt.util.password.PasswordEncryptor passwordEncryptor = ") + (hasSetPasswordEncryptor ? "new org.jasypt.util.password.StrongPasswordEncryptor()" : "null")) + ";\n") + "    \n") + "    @Override\n") + "    public String encodePassword(String rawPass, Object salt) {\n") + "        if (passwordEncryptor == null) {\n") + "            throw new IllegalStateException(\"passwordEncryptor must be set before using the encoder\");\n") + "        }\n") + "        return passwordEncryptor.encryptPassword(rawPass);\n") + "    }\n") + "    \n") + "    @Override\n") + "    public boolean isPasswordValid(String encPass, String rawPass, Object salt) {\n") + "        if (passwordEncryptor == null) {\n") + "            throw new IllegalStateException(\"passwordEncryptor must be set before using the encoder\");\n") + "        }\n") + "        return passwordEncryptor.checkPassword(rawPass, encPass);\n") + "    }\n") + "    \n") + "    public void setPasswordEncryptor(org.jasypt.util.password.PasswordEncryptor passwordEncryptor) {\n") + "        this.passwordEncryptor = passwordEncryptor;\n") + "    }\n") + "}";
                        // Create an expression - anonymous class instantiation
                        constructorCall.replace(factory.createCodeSnippetExpression(adapterCode));
                        java.lang.System.out.println("Replaced PasswordEncoder instantiation in " + clazz.getQualifiedName());
                        modified = true;
                    } else if ("org.jasypt.spring.security.PBEPasswordEncoder".equals(typeName)) {
                        // Check if there's a setPbeStringEncryptor call
                        boolean hasSetPbeStringEncryptor = false;
                        if (parentStatement != null) {
                            java.util.List<spoon.reflect.code.CtInvocation<?>> invocations = spoon.reflect.visitor.Query.getElements(parentStatement, new spoon.reflect.visitor.filter.TypeFilter<spoon.reflect.code.CtInvocation<?>>(spoon.reflect.code.CtInvocation.class) {
                                @java.lang.Override
                                public boolean matches(spoon.reflect.code.CtInvocation<?> invocation) {
                                    return (invocation.getExecutable() != null) && invocation.getExecutable().getSimpleName().equals("setPbeStringEncryptor");
                                }
                            });
                            hasSetPbeStringEncryptor = !invocations.isEmpty();
                        }
                        // Create an anonymous inner class that implements PasswordEncoder
                        // This replaces: new PBEPasswordEncoder()
                        // With an implementation that delegates to StandardPBEStringEncryptor
                        java.lang.String adapterCode = (((((((((((((((((((((((((("new org.acegisecurity.providers.encoding.PasswordEncoder() {\n" + "    private org.jasypt.encryption.pbe.StandardPBEStringEncryptor pbeStringEncryptor = null;\n") + "    \n") + "    @Override\n") + "    public String encodePassword(String rawPass, Object salt) {\n") + "        if (pbeStringEncryptor == null) {\n") + "            throw new IllegalStateException(\"pbeStringEncryptor must be set before using the encoder\");\n") + "        }\n") + "        return pbeStringEncryptor.encrypt(rawPass);\n") + "    }\n") + "    \n") + "    @Override\n") + "    public boolean isPasswordValid(String encPass, String rawPass, Object salt) {\n") + "        if (pbeStringEncryptor == null) {\n") + "            throw new IllegalStateException(\"pbeStringEncryptor must be set before using the encoder\");\n") + "        }\n") + "        try {\n") + "            String decrypted = pbeStringEncryptor.decrypt(encPass);\n") + "            return decrypted.equals(rawPass);\n") + "        } catch (Exception e) {\n") + "            return false;\n") + "        }\n") + "    }\n") + "    \n") + "    public void setPbeStringEncryptor(org.jasypt.encryption.pbe.StandardPBEStringEncryptor pbeStringEncryptor) {\n") + "        this.pbeStringEncryptor = pbeStringEncryptor;\n") + "    }\n") + "}";
                        // Create an expression - anonymous class instantiation
                        constructorCall.replace(factory.createCodeSnippetExpression(adapterCode));
                        java.lang.System.out.println("Replaced PBEPasswordEncoder instantiation in " + clazz.getQualifiedName());
                        modified = true;
                    }
                }
                // Also handle setPasswordEncryptor and setPbeStringEncryptor method calls
                // These need to be transformed to setter calls on our adapters
                java.util.List<spoon.reflect.code.CtInvocation<?>> methodCalls = spoon.reflect.visitor.Query.getElements(clazz, new spoon.reflect.visitor.filter.TypeFilter<spoon.reflect.code.CtInvocation<?>>(spoon.reflect.code.CtInvocation.class) {
                    @java.lang.Override
                    public boolean matches(spoon.reflect.code.CtInvocation<?> invocation) {
                        if (invocation.getExecutable() != null) {
                            java.lang.String methodName = invocation.getExecutable().getSimpleName();
                            return "setPasswordEncryptor".equals(methodName) || "setPbeStringEncryptor".equals(methodName);
                        }
                        return false;
                    }
                });
                for (spoon.reflect.code.CtInvocation<?> methodCall : methodCalls) {
                    java.lang.String methodName = methodCall.getExecutable().getSimpleName();
                    java.util.List<spoon.reflect.code.CtExpression<?>> arguments = methodCall.getArguments();
                    if (arguments.size() > 0) {
                        spoon.reflect.code.CtExpression<?> arg = arguments.get(0);
                        if ("setPasswordEncryptor".equals(methodName)) {
                            // Change to our adapter's setter
                            methodCall.replace(factory.createCodeSnippetExpression(((methodCall.getTarget() + ".setPasswordEncryptor(") + arg) + ")"));
                            java.lang.System.out.println("Transformed setPasswordEncryptor in " + clazz.getQualifiedName());
                            modified = true;
                        } else if ("setPbeStringEncryptor".equals(methodName)) {
                            // Change to our adapter's setter
                            methodCall.replace(factory.createCodeSnippetExpression(((methodCall.getTarget() + ".setPbeStringEncryptor(") + arg) + ")"));
                            java.lang.System.out.println("Transformed setPbeStringEncryptor in " + clazz.getQualifiedName());
                            modified = true;
                        }
                    }
                }
                if (modified) {
                    transformCount++;
                }
            }
        }
        java.lang.System.out.println(("Transformation complete. Modified " + transformCount) + " files.");
        // Write transformed files
        launcher.prettyprint();
    }
}
