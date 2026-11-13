// This is the broken code that won't compile with sonarlint-core 8.19.0.72745
public class BrokenCodeExample {
    public void exampleMethod() {
        AnalysisEngineConfiguration.builder()
            .addEnabledLanguages(globalConfig.getEnabledLanguages()) // This line causes compilation error
            .setClientPid(globalConfig.getClientPid())
            .setExtraProperties(globalConfig.extraProperties())
            .setWorkDir(globalConfig.getWorkDir())
            .setModulesProvider(globalConfig.getModulesProvider())
            .build();
    }
}