/* 
 * ------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright, 2003 - 2007
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
 * --------------------------------------------------------------------- *
 * 
 * History
 *   17.09.2007 (gabriel): created
 */
package org.knime.ext.r.node.local;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.knime.base.node.io.csvwriter.CSVWriter;
import org.knime.base.node.io.csvwriter.FileWriterSettings;
import org.knime.base.node.io.filereader.FileAnalyzer;
import org.knime.base.node.io.filereader.FileReaderNodeSettings;
import org.knime.base.node.io.filereader.FileTable;
import org.knime.core.data.DataTableSpec;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.util.MutableBoolean;

/**
 * 
 * @author Thomas Gabriel, University of Konstanz
 * @author Kilian Thiel, University of Konstanz
 */
public abstract class RLocalNodeModel extends NodeModel {

    private static final NodeLogger LOGGER = 
        NodeLogger.getLogger(RLocalNodeModel.class);
    
    /**
     * time (millisec) between two calls to checkCancel.
     */
    private static final int CANCEL_CHECK_INTERVAL = 1000;    
    
    /**
     * The temp directory.
     */
    protected static final File TEMP_PATH = 
        new File(System.getProperty("java.io.tmpdir"));
    
    /**
     * The delimiter used for creation of csv files.
     */
    protected static final String DELIMITER = "\t";
    
    private SettingsModelString m_rbinaryFileSettingsModel =
        RLocalNodeDialogPane.createRBinaryFile();
    
    
    private String m_setWorkingDirCmd = 
        "setwd(\"" + TEMP_PATH.getParent().replace('\\', '/') + "\");\n";
    
    
    private String m_readDataCmdPrefix = "R <- read.csv(\"";
    
    private String m_readDataCmdSufffix = "\", header = TRUE);\n";
    
    
    private String m_writeDataCmdPrefix = "write.csv(R, \"";
    
    private String m_writeDataCmdSuffix = "\", row.names = FALSE);\n";
    
    
    
    /**
     * Constructor of <code>RLocalNodeModel</code> creating a model with one
     * data in port an one data out port.
     */
    public RLocalNodeModel() {
        super(1, 1);
    }
    
    /**
     * Constructor of <code>RLocalNodeModel</code> with given numbers of
     * input data tables and output data tables. 
     * 
     * @param nrDataIns The number of input data tables.
     * @param nrDataOuts The number of output data tables.
     */
    public RLocalNodeModel(final int nrDataIns, final int nrDataOuts) {
        super(nrDataIns, nrDataOuts);
    }

    /**
     * Constructor of <code>RLocalNodeModel</code> with given numbers of
     * input data tables and output data tables as well as the numbers of
     * input and output predictor parameters. 
     * 
     * @param nrDataIns The number of input data tables.
     * @param nrDataOuts The number of output data tables.
     * @param nrModelIns The number of input predictor parameters.
     * @param nrModelOuts The number of output predictor parameters.
     */
    public RLocalNodeModel(final int nrDataIns, final int nrDataOuts, 
            final int nrModelIns, final int nrModelOuts) {
        super(nrDataIns, nrDataOuts, nrModelIns, nrModelOuts);
    }
    
    /**
     * @return The R command to execute.
     */
    protected abstract String getCommand(); 
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected BufferedDataTable[] execute(final BufferedDataTable[] inData,
            final ExecutionContext exec) throws CanceledExecutionException,
            Exception {
        
        // write data to csv
        File inDataCsvFile = writeInDataCsvFile(inData[0], exec);
        
        // execute R cmd
        StringBuffer completeCmd = new StringBuffer();
        completeCmd.append(m_setWorkingDirCmd);
        completeCmd.append(m_readDataCmdPrefix);
        completeCmd.append(inDataCsvFile.getAbsolutePath().
                replace('\\', '/'));
        completeCmd.append(m_readDataCmdSufffix);
        
        completeCmd.append(getCommand());
        completeCmd.append("\n");
        
        File tempOutData = 
            File.createTempFile("R-outDataTempFile-", ".csv", TEMP_PATH);
        completeCmd.append(m_writeDataCmdPrefix);
        completeCmd.append(tempOutData.getAbsolutePath().replace('\\', '/'));
        completeCmd.append(m_writeDataCmdSuffix);
        
        // write R command
        String rCmd = completeCmd.toString();
        LOGGER.info("R command: \n" + rCmd);
        File rCommandFile = writeRcommandFile(rCmd);
        File rOutFile = new File(rCommandFile.getAbsolutePath() + ".Rout");
        
        // create shell command
        StringBuffer shellCmd = new StringBuffer();
        shellCmd.append(m_rbinaryFileSettingsModel.getStringValue());
        shellCmd.append(" CMD BATCH ");
        shellCmd.append(rCommandFile.getAbsolutePath());
        
        
        // execute shell command
        int exitVal;
        String shcmd = shellCmd.toString();
        LOGGER.info("Shell command: \n" + shcmd);
        
        //
        //---------------------------------------------------------------------
        /// Put all this code into a new class remaining to the exttool package
        /// which should be moved into base !
        //
        final Process proc = Runtime.getRuntime().exec(shcmd);
        
        // create a thread that periodically checks for user cancellation
        final MutableBoolean procDone = new MutableBoolean(false);
        new Thread(new Runnable() {
            public void run() {
                synchronized (procDone) {
                    while (!procDone.booleanValue()) {
                        try {
                            exec.checkCanceled();
                        } catch (CanceledExecutionException cee) {
                            // blow away the running external process
                            // doesn't kill sub processes though!
                            proc.destroy();
                            return;
                        }
                        try {
                            procDone.wait(CANCEL_CHECK_INTERVAL);
                        } catch (InterruptedException e) {
                            // do nothing
                        }
                    }
                }
            }

        }).start();        
        
        // wait until the external process finishes.
        exitVal = proc.waitFor();

        synchronized (procDone) {
            // this should terminate the check cancel thread
            procDone.setValue(true);
        }

        exec.checkCanceled();
        exec.setProgress("Wrapping up");
        LOGGER.info("External executable 'R' terminated with exit code: " 
                + exitVal);
        //
        /// End of the code passage.
        //---------------------------------------------------------------------
        //
        
        
        
        // read data from R output csv into a buffered data table.
        BufferedDataTable dt = readOutData(tempOutData, exec);
        
        // delete all temp files
        if (inDataCsvFile.exists()) {
            if (inDataCsvFile.delete()) {
                LOGGER.debug(inDataCsvFile.getAbsoluteFile() 
                        + " could not be deleted !");
            }
        }
        if (tempOutData.exists()) {
            if (tempOutData.delete()) {
                LOGGER.debug(tempOutData.getAbsoluteFile() 
                        + " could not be deleted !");
            }
        }
        if (rCommandFile.exists()) {
            if (rCommandFile.delete()) {
                LOGGER.debug(rCommandFile.getAbsoluteFile() 
                        + " could not be deleted !");
            }
        }
        if (rOutFile.exists()) {
            if (rOutFile.delete()) {
                LOGGER.debug(rOutFile.getAbsoluteFile() 
                        + " could not be deleted !");
            }
        }
        
        // return this table
        return new BufferedDataTable[]{dt};
    }
    
    
    
    private File writeRcommandFile(final String cmd) throws IOException {
        File tempCommandFile = 
            File.createTempFile("R-inDataTempFile-", ".r", TEMP_PATH);
        FileWriter fw = new FileWriter(tempCommandFile);
        fw.write(cmd);
        fw.close();
        return tempCommandFile;
    }
    
    private File writeInDataCsvFile(final BufferedDataTable inData, 
            final ExecutionContext exec) throws IOException, 
            CanceledExecutionException {
        // create Temp file
        File tempInDataFile = 
            File.createTempFile("R-inDataTempFile-", ".csv", TEMP_PATH);
            
        // write data to file
        FileWriter fw = new FileWriter(tempInDataFile);
        FileWriterSettings fws = new FileWriterSettings();
        fws.setColSeparator(DELIMITER);
        fws.setWriteColumnHeader(true);
        CSVWriter writer = new CSVWriter(fw);
        writer.write(inData, exec);
        writer.close();
        return tempInDataFile;
    }
    
    private BufferedDataTable readOutData(final File outData, 
            final ExecutionContext exec) throws IOException, 
            CanceledExecutionException {
        FileReaderNodeSettings settings = new FileReaderNodeSettings();
        settings.setDataFileLocationAndUpdateTableName(
                outData.toURI().toURL());
        settings.addDelimiterPattern(DELIMITER, false, false, false);
        settings.addRowDelimiter("\n", true);
        settings.addQuotePattern("\"", "\"");
        settings.setDelimiterUserSet(true);
        settings.setFileHasColumnHeaders(true);
        settings.setFileHasColumnHeadersUserSet(true);
        settings.setFileHasRowHeaders(false);
        settings.setFileHasRowHeadersUserSet(true);
        settings.setQuoteUserSet(true);
        settings.setWhiteSpaceUserSet(true);
        settings = FileAnalyzer.analyze(settings);
        
        DataTableSpec tSpec = settings.createDataTableSpec();
        FileTable fTable = new FileTable(tSpec, settings, settings
                    .getSkippedColumns(), exec);
        
        return exec.createBufferedDataTable(fTable, exec);
    }
    
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        readSettings(settings, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
        // Nothing to reset ...
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        m_rbinaryFileSettingsModel.saveSettingsTo(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        readSettings(settings, true);
    }

    private void readSettings(final NodeSettingsRO settings,
            final boolean validateOnly) throws InvalidSettingsException {
        SettingsModelString tempBinaryFileModel = 
            m_rbinaryFileSettingsModel.createCloneWithValidatedValue(settings);
        File binaryFile = new File(tempBinaryFileModel.getStringValue());
        if (!binaryFile.exists() || !binaryFile.isFile() 
                || !binaryFile.canExecute()) {
            throw new InvalidSettingsException("File: "
                    + tempBinaryFileModel.getStringValue()
                    + " is not a valid R executable file !");
        }
        
        if (!validateOnly) {
            m_rbinaryFileSettingsModel.loadSettingsFrom(settings);
        }
    }
    
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveInternals(final File nodeInternDir, 
            final ExecutionMonitor exec) throws IOException, 
            CanceledExecutionException {
        // No internals to save ...
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadInternals(final File nodeInternDir, 
            final ExecutionMonitor exec) throws IOException, 
            CanceledExecutionException {
        // No internals to load ...
    }
}
