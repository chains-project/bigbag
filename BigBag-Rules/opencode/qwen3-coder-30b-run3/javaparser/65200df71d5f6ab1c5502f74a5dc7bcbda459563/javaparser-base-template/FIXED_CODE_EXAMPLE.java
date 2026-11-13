// This is the fixed code that compiles with sonarlint-core 8.19.0.72745
public class FixedCodeExample {
    public void exampleMethod() {
        AnalysisEngineConfiguration.builder()
            .addEnabledLanguage(globalConfig.getEnabledLanguages().toArray(new Language[0])) // Fixed version
            .setClientPid(globalConfig.getClientPid())
            .setExtraProperties(globalConfig.extraProperties())
            .setWorkDir(globalConfig.getWorkDir())
            .setModulesProvider(globalConfig.getModulesProvider())
            .build();
    }
}