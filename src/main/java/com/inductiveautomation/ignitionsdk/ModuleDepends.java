package com.inductiveautomation.ignitionsdk;

public class ModuleDepends {

    private String scope;
    private String moduleId;

    /**
     * Whether the dependency is marked as required. When {@code true}, {@code required="true"} is written on this
     * module's {@code <depends>} element in module.xml; when {@code false} (the default), the attribute is omitted.
     * How the attribute is enforced is up to the Gateway loading the module.
     */
    private boolean required;

    public String getScope() {
        return scope;
    }

    public String getModuleId() {
        return moduleId;
    }

    public boolean isRequired() {
        return required;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public void setModuleId(String moduleId) {
        this.moduleId = moduleId;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

}
