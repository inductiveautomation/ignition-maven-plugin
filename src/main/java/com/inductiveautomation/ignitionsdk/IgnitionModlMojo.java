package com.inductiveautomation.ignitionsdk;

import com.google.common.collect.Sets;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.zeroturnaround.zip.ZipUtil;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Builds the Ignition module
 * */
@Mojo(name = "modl",
    defaultPhase = LifecyclePhase.PACKAGE,
    requiresDependencyResolution = ResolutionScope.COMPILE,
    requiresDependencyCollection = ResolutionScope.COMPILE)
public class IgnitionModlMojo extends AbstractMojo {
    /**
     * The {@link MavenProject}.
     */
    @Parameter( defaultValue = "${project}", readonly = true )
    private MavenProject project;

    @Parameter( defaultValue = "${project.collectedProjects}", readonly = true )
    private List<MavenProject> projects;

    /**
     * The {@link MavenProject}.
     */
    @Parameter( defaultValue = "${project.artifacts}", readonly = true )
    private Set<Artifact> artifacts;

    /**
     * The {@link ProjectScope}s; used to map dependencies from a given Maven project to an Ignition scope.
     */
    @Parameter(required = true)
    private ProjectScope[] projectScopes;

    /**
     * The id of the Ignition module.
     */
    @Parameter(required = true)
    private String moduleId;

    /**
     * The name of the Ignition module, e.g. "Turbo Encabulator Module"
     */
    @Parameter(required = true)
    private String moduleName;

    /**
     * The description of the Ignition module, e.g.
     * <p/>
     * "For a number of years now, work has been proceeding in order to bring perfection to the crudely conceived idea
     * of a transmission that would not only supply inverse reactive current for use in unilateral phase detractors,
     * but would also be capable of automatically synchronizing cardinal grammeters. Such an instrument is the
     * turbo-encabulator."
     */
    @Parameter(required = false)
    private String moduleDescription;

    /**
     * The version of the Ignition module, e.g. "1.7.0.13".
     */
    @Parameter(defaultValue = "${project.version}", required = true)
    private String moduleVersion;

    /**
     * Allows the module to skip the Pack200 process.  May be useful in cases where language levels clash and packed
     * modules are faulting during startup on lower Java levels.
     *
     * @deprecated CD scoped compression is skipped due to the impending removal of Pack200. Please remove
     * this value.
     */
    @Deprecated
    @Parameter(defaultValue = "false")
    private String skipClientCompression;

    /**
     * The minimum required Ignition version.
     */
    @Parameter(required = true)
    private String requiredIgnitionVersion;

    /**
     * The required framework versions.
     */
    @Parameter(required = false)
    private int requiredFrameworkVersion = -1;

    /**
     * The name of the license file.
     */
    @Parameter(required = false)
    private String licenseFile;

    /**
     * Specify if the module is a free module.  Defaults to false if not supplied.
     */
    @Deprecated
    @Parameter(required = false, defaultValue = "false")
    private String freeModule;

    /**
     * The name of the documentation index file.
     */
    @Parameter(required = false)
    private String documentationFile;

    /**
     * Scope and module id of Ignition modules this module depends on.
     */
    @Parameter
    private ModuleDepends[] depends;

    /**
     * Scope and class names of the hooks provided by this module.
     */
    @Parameter(required = true)
    private ModuleHook[] hooks;

    /**
     * The name of an optional properties file that can be used to add or modify properties configured on the Packer.
     *
     * @deprecated CD scoped compression is skipped by default due to the impending removal of Pack200. Please remove
     * this value.
     */
    @Deprecated
    @Parameter
    private String packPropsFilename;

    private final Set<Artifact> clientScopeArtifacts = new HashSet<>();
    private final Set<Artifact> designerScopeArtifacts = new HashSet<>();
    private final Set<Artifact> gatewayScopeArtifacts = new HashSet<>();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Map<String, String> ignitionScopes = new HashMap<>();

        for (ProjectScope ps : projectScopes) {
            ignitionScopes.put(ps.getName(), ps.getScope());
        }

        MavenProject parent = project;
        if (project.hasParent()) {
            parent = project.getParent();
        }

        for (MavenProject p : parent.getCollectedProjects()) {
            String ignitionScope = ignitionScopes.get(p.getArtifactId());

            getLog().info(String.format("project=%s, ignitionScope=%s", p.getName(), ignitionScope));

            // maven project requires that an artifact filter be set to return artifacts from the project
            p.setArtifactFilter(new ScopeArtifactFilter("compile"));
            Set<Artifact> artifacts = p.getArtifacts();
            getLog().info(String.format("Found %d artifacts for project: %s",
                    artifacts.size(), p.getName()));

            if (StringUtils.contains(ignitionScope, "C")) {
                getLog().info("building client scoped artifact set...");

                clientScopeArtifacts.add(p.getArtifact());

                for (Artifact artifact : artifacts) {
                    if ("compile".equals(artifact.getScope())) {
                        clientScopeArtifacts.add(artifact);
                    }
                }
            }

            if (StringUtils.contains(ignitionScope, "D")) {
                getLog().info("building designer scoped artifact set...");

                designerScopeArtifacts.add(p.getArtifact());

                for (Artifact artifact : artifacts) {
                    if ("compile".equals(artifact.getScope())) {
                        designerScopeArtifacts.add(artifact);
                    }
                }
            }

            if (StringUtils.contains(ignitionScope, "G")) {
                getLog().info("building gateway scoped artifact set...");

                gatewayScopeArtifacts.add(p.getArtifact());

                for (Artifact artifact : artifacts) {
                    if ("compile".equals(artifact.getScope())) {
                        gatewayScopeArtifacts.add(artifact);
                    }
                }
            }
        }

        Path tempDirPath;
        try {
            tempDirPath = Files.createTempDirectory("modl");
        } catch (IOException e) {
            throw new MojoExecutionException("Error creating temp directory.", e);
        }

        copyArtifacts(tempDirPath);
        writeModuleXml(tempDirPath);
        createModlFile(tempDirPath);
    }

    private void copyArtifacts(Path tempDirPath) throws MojoExecutionException {
        getLog().info("Using temp dir: " + tempDirPath);

        try {
            getLog().info("copying g artifacts");
            for (Artifact artifact : gatewayScopeArtifacts) {
                // The artifact obtained from MavenProject#getArtifact() has a null scope.
                if (artifact.getScope() == null ||
                    StringUtils.equals("compile", artifact.getScope())) {

                    String artifactFileName = artifact.getArtifactId() + "-" + artifact.getVersion() + ".jar";
                    getLog().info("copying dependency artifact: " + artifactFileName);

                    File artifactFile = new File(tempDirPath.toFile(), artifactFileName);

                    Files.copy(new FileInputStream(artifact.getFile()), artifactFile.toPath());
                }
            }

            getLog().info("copying c+d artifacts");
            for (Artifact artifact : Sets.union(clientScopeArtifacts, designerScopeArtifacts)) {
                // The artifact obtained from MavenProject#getArtifact() has a null scope.
                if (artifact.getScope() == null ||
                    StringUtils.equals("compile", artifact.getScope())) {

                    String artifactFileName = artifact.getArtifactId() + "-" + artifact.getVersion() + ".jar";
                    getLog().info("'skipClientCompression' is true, skipping c+d packing and copying dependency artifact: " + artifact.getArtifactId() + ".jar");

                    File artifactFile = new File(tempDirPath.toFile(), artifactFileName);
                    if (!artifactFile.exists()) {
                        Files.copy(artifact.getFile().toPath(), artifactFile.toPath());
                    }

                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Error copying dependency artifacts.", e);
        }
    }

    private void writeModuleXml(Path tempDirPath) throws MojoExecutionException {
        try {
            XMLOutputFactory factory = XMLOutputFactory.newFactory();
            File moduleXmlFile = new File(tempDirPath.toFile(), "module.xml");
            getLog().info("creating module.xml: " + moduleXmlFile);

            if (!moduleXmlFile.createNewFile()) {
                throw new IOException("unable to create module.xml");
            }

            XMLStreamWriter writer = factory.createXMLStreamWriter(new FileOutputStream(moduleXmlFile), "UTF-8");

            writer.writeStartDocument("UTF-8", "1.0");
            writer.writeStartElement("modules");
            writer.writeStartElement("module");

            writer.writeStartElement("id");
            writer.writeCharacters(moduleId);
            writer.writeEndElement();

            writer.writeStartElement("name");
            writer.writeCharacters(moduleName);
            writer.writeEndElement();

            writer.writeStartElement("description");
            writer.writeCharacters(moduleDescription);
            writer.writeEndElement();

            writer.writeStartElement("version");
            writer.writeCharacters(moduleVersion);
            writer.writeEndElement();

            writer.writeStartElement("requiredignitionversion");
            writer.writeCharacters(requiredIgnitionVersion);
            writer.writeEndElement();

            if (requiredFrameworkVersion != -1) {
                writer.writeStartElement("requiredframeworkversion");
                writer.writeCharacters(String.valueOf(requiredFrameworkVersion));
                writer.writeEndElement();
            }

            if (licenseFile != null) {
                writer.writeStartElement("license");
                writer.writeCharacters(licenseFile);
                writer.writeEndElement();
            }

            if (documentationFile != null) {
                writer.writeStartElement("documentation");
                if (documentationFile.startsWith("doc/")) {
                    writer.writeCharacters(documentationFile.substring(4));
                } else {
                    writer.writeCharacters(documentationFile);
                }
                writer.writeEndElement();
            }

            if (depends != null) {
                for (ModuleDepends d : depends) {
                    writer.writeStartElement("depends");
                    writer.writeAttribute("scope", d.getScope());
                    writer.writeCharacters(d.getModuleId());
                    writer.writeEndElement();
                }
            }

            for (Artifact artifact : gatewayScopeArtifacts) {
                String artifactFileName = artifact.getArtifactId() + "-" + artifact.getVersion() + ".jar";
                String scope = "G";

                writer.writeStartElement("jar");
                writer.writeAttribute("scope", scope);
                writer.writeCharacters(artifactFileName);
                writer.writeEndElement();
            }

            for (Artifact artifact : Sets.union(clientScopeArtifacts, designerScopeArtifacts)) {
                String artifactFileName = artifact.getArtifactId() + "-" + artifact.getVersion() + ".jar";

                String scope = "";
                if (clientScopeArtifacts.contains(artifact)) {
                    scope += "C";
                }
                if (designerScopeArtifacts.contains(artifact)) {
                    scope += "D";
                }

                if (!scope.isEmpty()) {
                    writer.writeStartElement("jar");
                    writer.writeAttribute("scope", scope);
                    writer.writeCharacters(artifactFileName);
                    writer.writeEndElement();
                }
            }

            for (ModuleHook h : hooks) {
                writer.writeStartElement("hook");
                writer.writeAttribute("scope", h.getScope());
                writer.writeCharacters(h.getHookClass());
                writer.writeEndElement();
            }

            writer.writeEndElement();
            writer.writeEndElement();
            writer.writeEndDocument();

            writer.close();
        } catch (IOException | XMLStreamException e) {
            throw new MojoExecutionException("Error copying dependency artifacts.", e);
        }
    }

    private void createModlFile(Path tempDirPath) throws MojoExecutionException {
        try {
            File buildDir = new File(project.getBuild().getDirectory());
            if (!buildDir.exists() && !buildDir.mkdirs()) {
                throw new Exception("Could not create file: " + buildDir);
            }

            // grab any docs or license we need and copy them to the tempDir
            if (findDocs(tempDirPath)) {
                getLog().info("Adding documentation to module.");
            }

            if (findLicense(tempDirPath).isPresent()) {
                getLog().info("License file added to module.");
            }

            String unsignedModuleName = StringUtils.replace(moduleName, ' ', '-') + "-unsigned.modl";

            String filename = buildDir.getAbsolutePath() + File.separator + unsignedModuleName;


            getLog().info("Creating modl file at: " + filename);

            ZipUtil.pack(new File(tempDirPath.toString()), new File(filename));

        } catch (Exception e) {
            throw new MojoExecutionException("Error creating modl file.", e);
        }
    }

    /**
     * If a {@code documentationFile} element exists, copy everything the "doc" directory into the module.
     *
     * @param tempDir the path to the temp directory where the modl is being assembled.
     * @return {@code true} if documents were found and copied
     * @throws MojoExecutionException if copying the doc directory failed.
     */
    private boolean findDocs(Path tempDir) throws MojoExecutionException {
        if (documentationFile != null) {
            try {
                Path pathToDoc = Paths.get(project.getBasedir().getAbsolutePath(), "doc");
                FileUtils.copyDirectoryStructure(pathToDoc.toFile(), new File(tempDir.toFile(), "doc"));
                return true;
            } catch (IOException e) {
                getLog().warn("Failed to copy doc dir: " + e.getMessage(), e);
                throw new MojoExecutionException("failed to copy doc dir", e);
            }
        }

        return false;
    }


    /**
     * FindLicense looks for the license file based on the parameter specified in the plugin configs.  It will check
     * the build dir if no path is specified, otherwise it will try to find the license.html specified in a path
     * and copy it to the temp directory to be zipped.
     *
     * @param tempDir that contains the module assets to be packed into the .modl
     * @return an optional containing the {@link Path} to the new file if copied, else empty.
     * @throws MojoExecutionException when there is a failure to copy specified file
     */
    private Optional<Path> findLicense(Path tempDir) throws MojoExecutionException {
        // if user specified a license in the pom configuration
        if (licenseFile != null) {
            try {
                Path licensePath = licenseFile.contentEquals("license.html")
                    ? Paths.get(project.getBasedir() + File.separator + licenseFile)
                    : Paths.get(licenseFile);

                getLog().debug("Attempting to locate " + licensePath.toAbsolutePath());

                if (Files.exists(licensePath.toAbsolutePath()) && !Files.isDirectory(licensePath)) {
                    getLog().debug("License found in default directory, copying to " + tempDir.resolve(licenseFile));
                    return Optional.of(Files.copy(licensePath, tempDir.resolve("license.html")));
                } else {
                    throw new MojoExecutionException(String.format(
                        "License file '%s' was declared but not found.  Verify license.html path in build pom plugin "
                            + "configuration.",
                        licensePath.toAbsolutePath()));
                }
            } catch (IOException e) {
                throw new MojoExecutionException("Could locate license.html.  Check path in pom configuration.", e);
            }
        }
        return Optional.empty();
    }
}
