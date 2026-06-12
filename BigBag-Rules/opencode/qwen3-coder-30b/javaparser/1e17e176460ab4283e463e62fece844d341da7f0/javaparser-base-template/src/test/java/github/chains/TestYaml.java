package github.chains;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.BaseConstructor;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.resolver.Resolver;

public class TestYaml {
    public void testMethod() {
        // This is the old pattern that needs to be fixed
        BaseConstructor constructor = new BaseConstructor();
        Yaml yaml = new Yaml(constructor, new Representer(), new DumperOptions(), new Resolver());
        
        // This is the new pattern that works with SnakeYAML 2.0
        Yaml yaml2 = new Yaml(constructor);
    }
}