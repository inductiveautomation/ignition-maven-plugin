package com.inductiveautomation.ignitionsdk;

/**
 * Helpers for reasoning about the {@code requiredIgnitionVersion} of a module.
 */
final class IgnitionVersions {

    private IgnitionVersions() {
    }

    /**
     * Whether the given required Ignition version supports the {@code required} flag on module
     * dependencies (Ignition 8.3+). Mirrors the Gradle plugin's {@code usemoduleDependencySpecs()}
     * so the two build plugins gate the flag identically.
     *
     * @param requiredIgnitionVersion the configured minimum Ignition version (e.g. {@code "8.3.0"})
     * @return {@code true} if the version is 8.3 or newer, {@code false} otherwise (including null,
     *     empty, or unparseable versions)
     */
    static boolean supportsRequiredDependencyFlag(String requiredIgnitionVersion) {
        if (requiredIgnitionVersion == null || requiredIgnitionVersion.isEmpty()) {
            return false;
        }

        String[] parts = requiredIgnitionVersion.split("\\.");
        try {
            int major = Integer.parseInt(parts[0]);
            if (major >= 2027) {
                return true;
            }
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return major == 8 && minor >= 3;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}