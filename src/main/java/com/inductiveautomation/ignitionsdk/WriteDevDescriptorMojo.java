package com.inductiveautomation.ignitionsdk;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates a JSON dev module descriptor for IDE classloader isolation.
 * <p>
 * The descriptor contains module metadata (id, name, version, hooks, dependencies) along with
 * per-scope class output directories and resolved dependency JAR paths. This enables a dev Ignition
 * gateway to load the module directly from Maven build outputs instead of requiring a full .modl build.
 * <p>
 * The descriptor references each subproject's {@code target/classes}, so the module must be compiled
 * first. Run it after compilation — e.g. {@code mvn compile ignition:write-dev-descriptor}, or bind it
 * to a post-compile phase — otherwise the class directories are omitted from the descriptor.
 * <p>
 * Usage: {@code mvn ignition:write-dev-descriptor}
 */
@Mojo(name = "write-dev-descriptor",
    defaultPhase = LifecyclePhase.COMPILE,
    requiresDependencyResolution = ResolutionScope.COMPILE,
    requiresDependencyCollection = ResolutionScope.COMPILE)
public class WriteDevDescriptorMojo extends AbstractMojo {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(required = true)
    private ProjectScope[] projectScopes;

    @Parameter(required = true)
    private String moduleId;

    @Parameter(required = true)
    private String moduleName;

    @Parameter(defaultValue = "${project.version}", required = true)
    private String moduleVersion;

    @Parameter(defaultValue = "false")
    private boolean freeModule;

    @Parameter(required = true)
    private String requiredIgnitionVersion;

    @Parameter
    private ModuleDepends[] depends;

    @Parameter(required = true)
    private ModuleHook[] hooks;

    @Override
    public void execute() throws MojoExecutionException {
        MavenProject parent = project.hasParent() ? project.getParent() : project;

        // Build scope mapping: sub-project name -> scope letter(s)
        Map<String, String> ignitionScopes = new HashMap<>();
        for (ProjectScope ps : projectScopes) {
            ignitionScopes.put(ps.getName(), ps.getScope());
        }

        // Collect scope data from sub-projects
        Map<String, ScopeData> scopeMap = new LinkedHashMap<>();

        // Reactor modules (by groupId:artifactId) — their jars are excluded; we use class dirs instead.
        Set<String> reactorModuleIds = new HashSet<>();
        for (MavenProject mp : parent.getCollectedProjects()) {
            reactorModuleIds.add(mp.getGroupId() + ":" + mp.getArtifactId());
        }

        for (MavenProject p : parent.getCollectedProjects()) {
            String scope = ignitionScopes.get(p.getName());
            if (scope == null) {
                continue;
            }

            ScopeData data = scopeMap.computeIfAbsent(scope, k -> new ScopeData());

            // Class output directory (Maven convention: target/classes).
            // Maven's resource plugin copies resources into this same dir, so it covers both.
            File outDir = new File(p.getBuild().getOutputDirectory());
            if (outDir.isDirectory()) {
                data.classDirs.add(outDir.getAbsolutePath());
            }

            // Resolved dependency JARs — mirror the set that ignition:modl bundles into the .modl:
            // only compile-scoped third-party artifacts. The artifact filter must be set for a
            // collected (sibling) project's getArtifacts() to return anything (same as IgnitionModlMojo).
            p.setArtifactFilter(new ScopeArtifactFilter("compile"));
            for (Artifact artifact : p.getArtifacts()) {
                if (!"compile".equals(artifact.getScope())) {
                    continue;
                }
                // Exclude reactor project artifacts (other sub-modules) — we use class dirs for those.
                if (reactorModuleIds.contains(artifact.getGroupId() + ":" + artifact.getArtifactId())) {
                    continue;
                }
                if (artifact.getFile() != null && artifact.getFile().getName().endsWith(".jar")) {
                    data.jars.add(artifact.getFile().getAbsolutePath());
                }
            }
        }

        // Build the descriptor
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("id", moduleId);
        descriptor.put("name", moduleName);
        descriptor.put("version", moduleVersion);
        descriptor.put("freeModule", freeModule);

        // Hooks: scope -> className
        Map<String, String> hooksMap = new LinkedHashMap<>();
        if (hooks != null) {
            for (ModuleHook hook : hooks) {
                if (hooksMap.containsKey(hook.getScope())){
                    throw new MojoExecutionException(hook.getScope() + " has hookClass assigned already.");
                }
                hooksMap.put(hook.getScope(), hook.getHookClass());
            }
        }
        descriptor.put("hooks", hooksMap);

        // Module dependencies. The 'required' flag is only understood by Ignition 8.3+, so gate it
        // on requiredIgnitionVersion — same rule as the module.xml written by ignition:modl.
        boolean writeRequired = IgnitionVersions.supportsRequiredDependencyFlag(requiredIgnitionVersion);
        List<Map<String, Object>> depsList = new ArrayList<>();
        if (depends != null) {
            for (ModuleDepends dep : depends) {
                Map<String, Object> depMap = new LinkedHashMap<>();
                depMap.put("id", dep.getModuleId());
                depMap.put("scope", dep.getScope());
                if (writeRequired) {
                    depMap.put("required", dep.isRequired());
                }
                depsList.add(depMap);
            }
        }
        descriptor.put("moduleDependencies", depsList);

        // Scopes
        Map<String, Map<String, Set<String>>> scopesJson = new LinkedHashMap<>();
        for (Map.Entry<String, ScopeData> entry : scopeMap.entrySet()) {
            Map<String, Set<String>> scopeEntry = new LinkedHashMap<>();
            scopeEntry.put("classDirs", entry.getValue().classDirs);
            scopeEntry.put("jars", entry.getValue().jars);
            scopesJson.put(entry.getKey(), scopeEntry);
        }
        descriptor.put("scopes", scopesJson);

        // TODO: exports are not yet collected; emitted as an empty object to match the descriptor
        // schema (and the Gradle plugin's counterpart) until per-scope exports are wired up.
        descriptor.put("exports", new LinkedHashMap<>());

        // Write to build/dev/{moduleId}.json
        Path outputDir = Path.of(project.getBuild().getDirectory(), "dev");
        Path outputFile = outputDir.resolve(moduleId + ".json");

        try {
            Files.createDirectories(outputDir);
            Files.writeString(outputFile, GSON.toJson(descriptor), StandardCharsets.UTF_8);
            getLog().info("Wrote dev module descriptor: " + outputFile.toAbsolutePath());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write dev module descriptor", e);
        }
    }

    private static class ScopeData {
        final Set<String> classDirs = new LinkedHashSet<>();
        final Set<String> jars = new LinkedHashSet<>();
    }
}
