package com.inductiveautomation.ignitionsdk;

public class ModuleDepends {

    private String scope;
    private String moduleId;
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
