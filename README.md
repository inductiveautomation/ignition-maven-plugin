# Ignition Maven Plugin

This is the maven plugin used in the Ignition SDK examples to build Ignition Modules.

# Build and Install

Check out the repo, and execute `mvn clean install`


# Module Dependencies

Declare the Ignition modules your module depends on with `<depends>` entries in the plugin's `<configuration>`.
Set `<required>true</required>` to mark a dependency as required; this writes `required="true"` on the
`<depends>` element in the generated module.xml. Leave it out (or set it to `false`) for a non-required dependency.

```xml
<depends>
    <depend>
        <scope>G</scope>
        <moduleId>com.inductiveautomation.vision</moduleId>
        <required>true</required>
    </depend>
</depends>
```

This produces the following in module.xml:

```xml
<depends scope="G" required="true">com.inductiveautomation.vision</depends>
```
