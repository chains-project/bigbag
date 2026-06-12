import re

def fix_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()
    
    content = content.replace("            return properties;\n        };\n    }", "            return properties;\n        }\n    }")

    content = re.sub(r'import javax\.validation\..*?\n', '', content)
    content = re.sub(r'import org\.apache\.commons\.beanutils\.PropertyUtils;\n', '', content)
    
    with open(filepath, 'w') as f:
        f.write(content)

fix_file('/workspace/wicket-crudifier/spooned/com/premiumminds/wicket/crudifier/form/elements/ListControlGroups.java')
fix_file('/workspace/wicket-crudifier/spooned/com/premiumminds/wicket/crudifier/view/CrudifierView.java')
