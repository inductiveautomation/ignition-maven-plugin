/**
 * Created by pjones on 1/6/15.
 */
package com.inductiveautomation.ignitionsdk;


import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.io.FileUtils;
import org.codehaus.plexus.util.StringUtils;


/**
 * Posts the packaged module to a running instance of the Ignition Gateway.
 */
@Mojo(name = "post",
        defaultPhase = LifecyclePhase.INSTALL,
        requiresDependencyResolution = ResolutionScope.RUNTIME)
public class PostModuleMojo extends AbstractMojo {

    /**
     * The {@link MavenProject}.
     */
    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    /**
     * The url of the development gateway.  If not supplied, the URL will default to http://localhost:8088
     */
    @Parameter(required = false)
    private String gatewayAddress;

    /**
     * The unsigned module file name.
     */
    @Parameter(required = false)
    private File moduleFile;

    /**
     * The name of the module.
     */
    @Parameter(required = false)
    private String moduleName;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            getLog().debug("Attempting to load the following path: ");

            Path buildPath = Paths.get(project.getBuild().getDirectory());
            String modulePath = buildPath.toAbsolutePath() + File.separator +
                    StringUtils.replace(moduleName, ' ', '-') + ".modl";

            if (!FileUtils.fileExists(modulePath))
                modulePath = buildPath.toAbsolutePath() + File.separator +
                        StringUtils.replace(moduleName, ' ', '-') + "-unsigned.modl";

            getLog().info("Installing " + modulePath + " to gateway.");
            postModuleToGateway(Paths.get(modulePath));
        } catch (Exception e) {
            throw new MojoExecutionException("Could not post the module to the Gateway.", e);
        }
    }

    private void postModuleToGateway(Path modulePath) throws MojoExecutionException {
        URL gatewayUrl;
        final String modulePostURI = "/main/system/DeveloperModuleLoadingServlet";

        try {
            if (gatewayAddress == null) {
                gatewayUrl = new URL("http://localhost:8088" + modulePostURI);
            } else {
                gatewayUrl = new URL(gatewayAddress + modulePostURI);
            }
            getLog().info("Deploying to " + gatewayUrl.toString());
            HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();

            String b64 = Base64.encodeFromFile(modulePath.toString());
            getLog().debug("Successfully encoded " + modulePath.toString() + ".");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(gatewayUrl.toURI())
                    .POST(HttpRequest.BodyPublishers.ofString(b64))
                    .header("Content-Type", "multipart/form-data")
                    .expectContinue(false)
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            getLog().debug(String.format("Successfully connected to %s%s", gatewayUrl.toString(), modulePostURI));
            getLog().debug(response.body());
        } catch (Exception e) {
            getLog().error(e);
            throw new MojoExecutionException("Could not post module to gateway.", e);
        }
    }
}
