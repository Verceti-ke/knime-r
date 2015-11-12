/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME GmbH, Konstanz, Germany
 *  Website: http://www.knime.org; Email: contact@knime.org
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME GMBH herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   13.06.2014 (hofer): created
 */
package org.knime.ext.r.bin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;

import org.knime.core.node.KNIMEConstants;
import org.knime.core.node.NodeLogger;
import org.knime.core.util.FileUtil;
import org.knime.ext.r.bin.preferences.DefaultRPreferenceProvider;
import org.knime.ext.r.bin.preferences.RPreferenceInitializer;
import org.knime.ext.r.bin.preferences.RPreferenceProvider;

import com.sun.jna.Platform;

/**
 * Utility class with methods to call R binary.
 *
 * @author Heiko Hofer
 */
public class RBinUtil {
    /**
     * The temp directory used as a working directory for R
     */
    static final String TEMP_PATH = KNIMEConstants.getKNIMETempDir().replace('\\', '/');

    static NodeLogger LOGGER = NodeLogger.getLogger(RBinUtil.class);

    /**
     * Exception thrown when the specified R_HOME directory is invalid.
     *
     * @author Jonathan Hale
     */
    public static class InvalidRHomeException extends Exception {

        /** Generated serialVersionUID */
        private static final long serialVersionUID = -4082365839749450179L;

        /**
         * Constructor
         *
         * @param msg error message
         */
        public InvalidRHomeException(final String msg) {
            super(msg);
        }

        /**
         * Constructor
         *
         * @param msg error message
         * @param parent Throwable which caused this exception
         */
        public InvalidRHomeException(final String msg, final Throwable cause) {
            super(cause);
        }
    }

    /**
     * Get properties about the used R.
     *
     * @return properties about use R
     * @throws IOException in case that running R fails
     * @throws InterruptedException when external process of calling R is interrupted
     */
    public static Properties retrieveRProperties() throws IOException, InterruptedException {
        return retrieveRProperties(RPreferenceInitializer.getRProvider());
    }

    /**
     * Get properties about the used R installation.
     *
     * @param rpref provider for path to R executable
     * @return properties about use R
     * @throws IOException in case that running R fails
     * @throws InterruptedException when external process of calling R is interrupted
     */
    public static Properties retrieveRProperties(final RPreferenceProvider rpref) {
        final File tmpPath = new File(TEMP_PATH);
        File propsFile = null;
        File rOutFile = null;
        try {
            propsFile = FileUtil.createTempFile("R-propsTempFile-", ".r", true);
            rOutFile = FileUtil.createTempFile("R-propsTempFile-", ".Rout", tmpPath, true);
        } catch (IOException e2) {
            LOGGER.error("Could not create temporary files for R execution.");
            return new Properties();
        }

        final String propertiesPath = propsFile.getAbsolutePath().replace('\\', '/');
        final String script = "setwd('" + tmpPath.getAbsolutePath().replace('\\', '/') + "')\n"
            + "foo <- paste(names(R.Version()), R.Version(), sep='=')\n"
            + "foo <- append(foo, paste('memory.limit', memory.limit(), sep='='))\n"
            + "foo <- append(foo, paste('Rserve.path', find.package('Rserve', quiet=TRUE), sep='='))\n"
            + "foo <- append(foo, paste('rhome', R.home(), sep='='))\n" //
            + "write(foo, file='" + propertiesPath + "', ncolumns=1, append=FALSE, sep='\\n')\nq()";

        File rCommandFile = null;
        try {
            rCommandFile = writeRcommandFile(script);
        } catch (IOException e1) {
            LOGGER.error("Could not write R command file.");
            return new Properties();
        }
        ProcessBuilder builder = new ProcessBuilder();
        builder.command(rpref.getRBinPath("Rscript"), "--vanilla", rCommandFile.getName(), rOutFile.getName());
        builder.directory(rCommandFile.getParentFile());

        /* Run R on the script to get properties */
        try {
            final Process process = builder.start();
            final BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            final BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            // Consume the output produced by the R process, otherwise may block process on some operating systems
            new Thread(() -> {
                try {
                    final StringBuilder b = new StringBuilder();
                    String line;
                    while ((line = outputReader.readLine()) != null) {
                        b.append(line);
                    }
                    LOGGER.debug("External Rscript process output: " + b.toString());
                } catch (Exception e) {
                    LOGGER.error("Error reading output of external R process.", e);
                }
            } , "R Output Reader").start();
            new Thread(() -> {
                try {
                    final StringBuilder b = new StringBuilder();
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        b.append(line);
                    }
                    LOGGER.debug("External Rscript process error output: " + b.toString());
                } catch (Exception e) {
                    LOGGER.error("Error reading error output of external R process.", e);
                }
            } , "R Error Reader").start();

            process.waitFor();
        } catch (Exception e) {
            LOGGER.debug(e.getMessage(), e);
            return new Properties();
        }

        // load properties from propsFile
        Properties props = new Properties();
        try {
            props.load(new FileInputStream(propsFile));
        } catch (IOException e) {
            LOGGER.warn("Could not retrieve properties from R.", e);
        }

        return props;
    }

    /**
     * Writes the given string into a file and returns it.
     *
     * @param cmd The string to write into a file.
     * @return The file containing the given string.
     * @throws IOException If string could not be written to a file.
     */
    private static File writeRcommandFile(final String cmd) throws IOException {
        File tempCommandFile = FileUtil.createTempFile("R-readPropsTempFile-", ".r", new File(TEMP_PATH), true);
        FileWriter fw = new FileWriter(tempCommandFile);
        fw.write(cmd);
        fw.close();
        return tempCommandFile;
    }

    /**
     * Checks whether the given path is a valid R_HOME directory. It checks the presence of the bin and library folder.
     *
     * @param rHomePath path to R_HOME
     * @throws InvalidRHomeException If the specified R_HOME path is invalid
     */
    public static void checkRHome(final String rHomePath) throws InvalidRHomeException {
        final File rHome = new File(rHomePath);
        final String msgSuffix =
            "R_HOME ('" + rHomePath + "') is meant to be the path to the folder which is the root of Rs "
                + "installation tree. \nIt contains a 'bin' folder which itself contains the R executable and a "
                + "'library' folder containing the R-Java bridge library.\n"
                + "Please change the R settings in the preferences.";
        /* check if the directory exists */
        if (!rHome.exists()) {
            throw new InvalidRHomeException("R_HOME does not exist." + msgSuffix);
        }
        /* Make sure R home is not a file. */
        if (!rHome.isDirectory()) {
            throw new InvalidRHomeException("R_HOME is not a directory." + msgSuffix);
        }
        /* Check if there is a bin directory */
        File binDir = new File(rHome, "bin");
        if (!binDir.isDirectory()) {
            throw new InvalidRHomeException("R_HOME does not contain a folder with name 'bin'." + msgSuffix);
        }
        /* Check if there is an R Excecutable */
        File rExecutable = new File(new DefaultRPreferenceProvider(rHomePath).getRBinPath("R"));
        if (!rExecutable.exists()) {
            throw new InvalidRHomeException("R_HOME does not contain an R executable." + msgSuffix);
        }
        /* Make sure there is a library directory */
        File libraryDir = new File(rHome, "library");
        if (!libraryDir.isDirectory()) {
            throw new InvalidRHomeException("R_HOME does not contain a folder with name 'library'." + msgSuffix);
        }
        /* On windows, we expect the appropriate platform-specific folders corresponding to our Platform */
        if (Platform.isWindows()) {
            if (Platform.is64Bit()) {
                File expectedFolder = new File(binDir, "x64");
                if (!expectedFolder.isDirectory()) {
                    throw new InvalidRHomeException(
                        "R_HOME does not contain a folder with name 'bin\\x64'. Please install R 64-bit files."
                            + msgSuffix);
                }
            } else {
                File expectedFolder = new File(binDir, "i386");
                if (!expectedFolder.isDirectory()) {
                    throw new InvalidRHomeException(
                        "R_HOME does not contain a folder with name 'bin\\i386'. Please install R 32-bit files."
                            + msgSuffix);
                }
            }
        }
    }
}
