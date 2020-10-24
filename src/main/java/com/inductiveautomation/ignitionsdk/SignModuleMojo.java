package com.inductiveautomation.ignitionsdk;

import com.google.common.base.Strings;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.StringUtils;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;

/**
 * Signs the module
 * */
@Mojo(name = "sign",
        defaultPhase = LifecyclePhase.INSTALL,
        requiresDependencyResolution = ResolutionScope.RUNTIME)
public class SignModuleMojo extends AbstractMojo {

    /**
     * The {@link MavenProject}.
     */
    @Parameter( defaultValue = "${project}", readonly = true )
    private MavenProject project;

    /**
     * The path to the keystore containing your code signing certificate.
     * Can either be JDS or PFX format.
     * */
    @Parameter(required = true)
    private String keystore;

    /**
     * The password to access they keystore.
     * */
    @Parameter(required = false)
    private String keystorePasswd;

    /**
     * The alias under which your code signing certificate is stored.
     * */
    @Parameter(required = true)
    private String alias;

    /**
     * The password to access the alias.
     * */
    @Parameter(required = false)
    private String aliasPasswd;

    /**
     * The filesystem path to the certificate chain (in p7b format).
     * This file is generally returned along with your signed certificate after submitting a CSR to a CA.
     */
    @Parameter(required = true)
    private String chain;

    /**
     * The unsigned module file name.
     */
    @Parameter(required = false)
    private File moduleFile;

    /**
     * Output file name of the module after signing
     * */
    @Parameter(required = false)
    private File signedFile;

    /**
     * The name of the module.
     */
    @Parameter(required = false)
    private String moduleName;

    /**
     * Information for a PKCS11 Repository (default is null)
     * */
    @Parameter(required = false)
    private String pkcs11Config;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        try {

            Path buildPath = Paths.get(project.getBuild().getDirectory());

            String unsignedPath = buildPath.toAbsolutePath() + File.separator +
                    StringUtils.replace(moduleName, ' ', '-') + "-unsigned.modl";

            String signedPath = buildPath.toAbsolutePath() + File.separator +
                    StringUtils.replace(moduleName, ' ', '-') + ".modl";

            getLog().debug(String.format("Attempting to sign from the following path: %s ", unsignedPath));
            getLog().info("Signing " + unsignedPath);

            if(Strings.isNullOrEmpty(keystorePasswd))
                keystorePasswd = getPasswordFromConsole("Enter the keystore password:");
            if(Strings.isNullOrEmpty(aliasPasswd))
                aliasPasswd = getPasswordFromConsole("Enter the alias password:");

            signModl(unsignedPath, signedPath);

        } catch (Exception e) {
            throw new MojoExecutionException("Could not sign the module", e);
        }
    }

    /**
     * Gets a password from the user
     * */
    private String getPasswordFromConsole(String prompt) throws Exception {
        Console console = System.console();

        if(console == null) throw new Exception("Couldn't get System Console Instance for Password Entry");

        console.printf(prompt);
        char[] passwd = console.readPassword();
        return new String(passwd);
    }

    /**
     *
     * */
    private void signModl(String infilePath, String outfilePath) throws Exception {

        KeyStore keyStore;

        if (!Strings.isNullOrEmpty(pkcs11Config)) {
            Provider p = Security.getProvider("SunPKCS11");
            p = p.configure(pkcs11Config);
            Security.addProvider(p);
            keyStore = KeyStore.getInstance("PKCS11");
            keyStore.load(null, keystorePasswd.toCharArray());
        } else {
            File keyStoreFile = new File(keystore);
            String keyStoreType = keyStoreFile.getCanonicalPath().endsWith("pfx") ? "pkcs12" : "jks";

            keyStore = KeyStore.getInstance(keyStoreType);
            keyStore.load(new FileInputStream(keyStoreFile), keystorePasswd.toCharArray());
        }

        Key privateKey = keyStore.getKey(alias, aliasPasswd.toCharArray());

        if (privateKey == null || !privateKey.getAlgorithm().equalsIgnoreCase("RSA")) {
            throw new MojoExecutionException("no RSA PrivateKey found for alias '" + alias + "'.");
        }

        InputStream chainInputStream = new FileInputStream(chain);

        File moduleIn = new File(infilePath);
        File moduleOut = new File(outfilePath);

        ModuleSigner moduleSigner = new ModuleSigner((PrivateKey) privateKey, chainInputStream);
        PrintStream printStream = new PrintStream(NullOutputStream.NULL_OUTPUT_STREAM);
        moduleSigner.signModule(printStream, moduleIn, moduleOut);
    }

}
