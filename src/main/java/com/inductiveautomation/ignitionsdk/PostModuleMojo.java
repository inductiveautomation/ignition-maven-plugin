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
    @Component
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
                    StringUtils.replace(moduleName, ' ', '-') + "-unsigned.modl";

            getLog().info("Installing " + modulePath + " to gateway.");

            postModuleToGateway(Paths.get(modulePath));
        } catch (Exception e) {
            throw new MojoExecutionException("Could not post the module to the Gateway.", e);
        }
    }

    private void postModuleToGateway(Path modulePath) throws MojoExecutionException {
        URL gatewayUrl;

        try {
            if (gatewayAddress == null) {
                gatewayUrl = new URL("http://localhost:8088/main/system/DeveloperModuleLoadingServlet");
            } else {
                gatewayUrl = new URL(gatewayAddress + "/main/system/DeveloperModuleLoadingServlet");
            }

            getLog().info("Deploying to " + gatewayUrl.toString());

            HttpURLConnection urlConn = (HttpURLConnection) gatewayUrl.openConnection();
            getLog().debug("Successfully connected to " + gatewayUrl.toString() + "/main/system/DeveloperModuleLoadingServlet");
            urlConn.setRequestMethod("POST");
            urlConn.setDoOutput(true);
            urlConn.setRequestProperty("Content-Type", "multipart/form-data");
            urlConn.setUseCaches(false);

            String b64 = Base64.encodeFromFile(modulePath.toString());
            getLog().debug("Successfully encoded " + modulePath.toString() + ".");

            OutputStreamWriter out = new OutputStreamWriter(urlConn.getOutputStream());
            out.write(b64);
            getLog().debug("Successfully wrote " + modulePath.toString() + " to Gateway.");

            out.flush();
            out.close();
            BufferedReader br = new BufferedReader(new InputStreamReader(urlConn.getInputStream()));
            String line = null;

            while ((line = br.readLine()) != null) {
                getLog().debug(line);
            }

            br.close();
        } catch (Exception e) {
            throw new MojoExecutionException("Could not post module to gateway.", e);
        }
    }
}
