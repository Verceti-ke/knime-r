/* ------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright, 2003 - 2013
 * University of Konstanz, Germany
 * Chair for Bioinformatics and Information Mining (Prof. M. Berthold)
 * and KNIME GmbH, Konstanz, Germany
 *
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner or
 * as specified in the license file distributed with this product.
 *
 * If you have any questions please contact the copyright holder:
 * website: www.knime.org
 * email: contact@knime.org
 * ---------------------------------------------------------------------
 *
 */
package org.knime.ext.r.node.local;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.LinkedList;

import org.knime.base.node.util.exttool.CommandExecution;
import org.knime.core.data.DataTableSpec;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.KNIMEConstants;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.ext.r.node.RDialogPanel;
import org.knime.ext.r.node.local.port.RPortObject;
import org.knime.ext.r.preferences.RPreferenceProvider;

/**
 *
 * @author Thomas Gabriel, KNIME.com AG, Zurich
 * @since 2.7
 */
public class RLocalRData2RNodeModel extends RAbstractLocalNodeModel {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(RLocalRData2RNodeModel.class);

    private String m_rCommand = "";

    /**
     * Creates a new instance of <code>RLocalRData2RNodeModel</code> with given in- and out-port specification.
     * @param pref provider for R executable
     */
    public RLocalRData2RNodeModel(final RPreferenceProvider pref) {
        super(new PortType[]{RPortObject.TYPE, BufferedDataTable.TYPE_OPTIONAL},
              new PortType[]{RPortObject.TYPE}, pref);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs)
            throws InvalidSettingsException {
        if ("".equals(m_rCommand)) {
            setWarningMessage("R script is missing; configure node.");
        }
        checkRExecutable();
        return new DataTableSpec[]{null};
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected PortObject[] execute(final PortObject[] inData, final ExecutionContext exec) throws Exception {

        // tmp files
        File inDataCsvFile = null;
        File rCommandFile = null;
        File rOutFile = null;
        RPortObject out;

        try {
         // execute R cmd
            StringBuilder completeCmd = new StringBuilder();
            completeCmd.append(SET_WORKINGDIR_CMD);

            // load model
            File fileR = ((RPortObject)inData[0]).getFile();
            completeCmd.append(LOAD_MODEL_CMD_PREFIX);
            completeCmd.append(fileR.getAbsolutePath().replace('\\', '/'));
            completeCmd.append(LOAD_MODEL_CMD_SUFFIX);

            // data in-port is optional
            if (inData[1] != null) {
                // preprocess data in in DataTable.
                PortObject[] inDataTables = preprocessDataTable(inData, exec);
                // write data to csv
                inDataCsvFile = writeInDataCsvFile((BufferedDataTable)inDataTables[1], exec);

                // write data into R
                completeCmd.append(READ_DATA_CMD_PREFIX);
                completeCmd.append(inDataCsvFile.getAbsolutePath().replace('\\', '/'));
                completeCmd.append(READ_DATA_CMD_SUFFIX);
            }

            // result R port
            completeCmd.append(FlowVariableResolver.parse(m_rCommand.trim(), this));
            completeCmd.append("\n");

            File outR = File.createTempFile("~knime", ".R", new File(KNIMEConstants.getKNIMETempDir()));
            outR.deleteOnExit();
            completeCmd.append("save(list = ls(all=TRUE), file=\"");
            completeCmd.append(outR.getAbsolutePath().replace('\\', '/'));
            completeCmd.append("\")\n");

            // write R command
            String rCmd = completeCmd.toString();
            LOGGER.debug("R Command: \n" + rCmd);
            rCommandFile = writeRcommandFile(rCmd);
            rOutFile = new File(rCommandFile.getAbsolutePath() + ".Rout");

            // create shell command
            StringBuilder shellCmd = new StringBuilder();

            final String rBinaryFile = getRBinaryPathAndArguments();
            shellCmd.append(rBinaryFile);
            shellCmd.append(" " + rCommandFile.getName());
            shellCmd.append(" " + rOutFile.getName());

            // execute shell command
            String shcmd = shellCmd.toString();
            LOGGER.debug("Shell command: \n" + shcmd);

            CommandExecution cmdExec = new CommandExecution(shcmd);
            cmdExec.addObserver(this);
            cmdExec.setExecutionDir(rCommandFile.getParentFile());
            int exitVal = cmdExec.execute(exec);

            setExternalErrorOutput(new LinkedList<String>(cmdExec.getStdErr()));
            setExternalOutput(new LinkedList<String>(cmdExec.getStdOutput()));

            String rErr = "";

            if (exitVal != 0) {
                // before we return, we save the output in the failing list
                synchronized (cmdExec) {
                    setFailedExternalOutput(new LinkedList<String>(cmdExec.getStdOutput()));
                }
            }
            synchronized (cmdExec) {

                // save error description of the Rout file to the ErrorOut
                LinkedList<String> list = new LinkedList<String>(cmdExec.getStdErr());

                list.add("#############################################");
                list.add("#");
                list.add("# Content of .Rout file: ");
                list.add("#");
                list.add("#############################################");
                list.add(" ");
                BufferedReader bfr = new BufferedReader(new FileReader(rOutFile));
                String line;
                while ((line = bfr.readLine()) != null) {
                    list.add(line);
                }
                bfr.close();

                // use row before last as R error.
                int index = list.size() - 2;
                if (index >= 0) {
                    rErr = list.get(index);
                }

                if (exitVal != 0) {
                    setFailedExternalErrorOutput(list);
                    LOGGER.debug("Execution of R Script failed with exit code: " + exitVal);
                    throw new IllegalStateException("Execution of R script failed: " + rErr);
                } else {
                    setExternalOutput(list);
                }
            }

            // generate R output
            out = new RPortObject(outR);

        } finally {
            // delete all temp files
            deleteFile(inDataCsvFile);
            deleteFile(rCommandFile);
            deleteFile(rOutFile);
        }

        // return R output
        return new PortObject[]{out};
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        super.loadValidatedSettingsFrom(settings);
        m_rCommand = RDialogPanel.getExpressionFrom(settings, "");
        try {
            m_argumentsR.loadSettingsFrom(settings);
        } catch (InvalidSettingsException ise) {
            // load old workflow no option is used, overwrite new dialog dft
            m_argumentsR.setStringValue("");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        super.saveSettingsTo(settings);
        RDialogPanel.setExpressionTo(settings, m_rCommand);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        super.validateSettings(settings);
    }

}
