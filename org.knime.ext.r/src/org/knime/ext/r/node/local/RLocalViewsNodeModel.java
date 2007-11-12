/* ------------------------------------------------------------------
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
 * ---------------------------------------------------------------------
 * 
 * History
 *   18.09.2007 (thiel): created
 */
package org.knime.ext.r.node.local;

import java.awt.Image;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.knime.core.data.DataTableSpec;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelIntegerBounded;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.util.FileUtil;
import org.knime.ext.r.node.RDialogPanel;
import org.knime.ext.r.node.RPlotterNodeModel;

/**
 * The <code>RLocalViewsNodeModel</code> provides functionality to create
 * a R script with user defined R code calling R plots, run it and display
 * the generated plot in the nodes view.
 * 
 * @author Kilian Thiel, University of Konstanz
 */
public class RLocalViewsNodeModel extends RLocalNodeModel {
    
    private static final String INTERNAL_FILE_NAME = "Rplot";
    
    private SettingsModelIntegerBounded m_heightModel = 
        RViewsPngDialogPanel.createHeightModel();
    
    private SettingsModelIntegerBounded m_widthModel = 
        RViewsPngDialogPanel.createWidthModel();
    
    private SettingsModelIntegerBounded m_pointSizeModel = 
        RViewsPngDialogPanel.createPointSizeModel();
    
    private SettingsModelString m_bgModel = 
        RViewsPngDialogPanel.createBgModel();
    
    private Image m_resultImage;
    
    private String m_filename;
    
    private String m_viewCmd = 
        RViewScriptingConstants.getDefaultExpressionCommand();
    
    
    /**
     * Creates new instance of <code>RLocalViewsNodeModel</code> with one data
     * in port and no data out port.
     */
    public RLocalViewsNodeModel() {
        super(false);
        m_resultImage = null;
    }
   
    /**
     * @return result image for the view, only available after successful
     *         execution of the node model.
     */
    Image getResultImage() {
        return m_resultImage;
    }    
    
    /**
     * Provides the R code to run, consisting of the <code>png()</code> command
     * to create a new png file, the plot command specified by the user and
     * the <code>dev.off()</code> command to shut down the standard graphic 
     * device.
     * 
     * {@inheritDoc}
     */
    @Override
    protected String getCommand() {
        return "png(\"" + m_filename + "\", width=" 
            + m_widthModel.getIntValue() + ", height=" 
            + m_heightModel.getIntValue() + ", pointsize=" 
            + m_pointSizeModel.getIntValue() + ", bg=\"" 
            + m_bgModel.getStringValue() + "\");\n" 
            + m_viewCmd
            + "\ndev.off();";
    }

    /**
     * After execution of the R code and image instance is created which can
     * be displayed by the nodes view.
     * 
     * {@inheritDoc}
     */
    @Override
    protected final BufferedDataTable[] postprocessDataTable(
            final BufferedDataTable[] outData, final ExecutionContext exec)
            throws CanceledExecutionException, Exception {
        
        // create image after execution.        
        FileInputStream fis = new FileInputStream(new File(m_filename));
        m_resultImage = RPlotterNodeModel.createImage(fis);
        fis.close();
        
        return new BufferedDataTable[]{};
    } 
    
    /**
     * Before execution of the R code the column filtering is done.
     * 
     * {@inheritDoc}
     */
    @Override
    protected final BufferedDataTable[] preprocessDataTable(
            final BufferedDataTable[] inData, final ExecutionContext exec)
            throws CanceledExecutionException, Exception {
        m_filename = 
            FileUtil.createTempDir("R_").getAbsolutePath().replace('\\', '/')
            + "/" + "R-View-" + System.identityHashCode(inData) + ".png";
        
        return inData;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected DataTableSpec[] configure(final DataTableSpec[] inSpecs)
            throws InvalidSettingsException {
        checkRExecutable();
        return new DataTableSpec[0];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        super.loadValidatedSettingsFrom(settings);
        m_heightModel.loadSettingsFrom(settings);
        m_widthModel.loadSettingsFrom(settings);
        m_pointSizeModel.loadSettingsFrom(settings);
        m_bgModel.loadSettingsFrom(settings);
        
        m_viewCmd = RDialogPanel.getExpressionFrom(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        super.saveSettingsTo(settings);
        m_heightModel.saveSettingsTo(settings);
        m_widthModel.saveSettingsTo(settings);
        m_pointSizeModel.saveSettingsTo(settings);
        m_bgModel.saveSettingsTo(settings);
        
        RDialogPanel.setExpressionTo(settings, m_viewCmd);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        super.validateSettings(settings);

        String viewCmd = RDialogPanel.getExpressionFrom(settings); 
        
        // if command not valid throw exception
        if (viewCmd == null || viewCmd.length() < 1) {
            throw new InvalidSettingsException("R View command is empty!");
        }
       
        m_heightModel.validateSettings(settings);
        m_widthModel.validateSettings(settings);
        m_pointSizeModel.validateSettings(settings);
        m_bgModel.validateSettings(settings);
    }
    
    
    /**
     * The saved image is loaded.
     * 
     * {@inheritDoc}
     */
    @Override
    protected void loadInternals(final File nodeInternDir, 
            final ExecutionMonitor exec) 
            throws IOException, CanceledExecutionException {
        super.loadInternals(nodeInternDir, exec);
        
        File file = new File(nodeInternDir, INTERNAL_FILE_NAME + ".png");
        if (file.exists() && file.canRead()) {
            File pngFile = File.createTempFile(INTERNAL_FILE_NAME, ".png");
            FileUtil.copy(file, pngFile);
            m_resultImage = RPlotterNodeModel.createImage(
                    new FileInputStream(pngFile));
            m_filename = pngFile.getAbsolutePath();
        }
    }

    /**
     * The created image is saved.
     * 
     * {@inheritDoc}
     */
    @Override
    protected void saveInternals(final File nodeInternDir, 
            final ExecutionMonitor exec) 
            throws IOException, CanceledExecutionException {
        super.saveInternals(nodeInternDir, exec);
        
        File imgFile = new File(m_filename);
        if (imgFile.exists() && imgFile.canWrite()) {
            File file = new File(nodeInternDir, INTERNAL_FILE_NAME + ".png");
            FileUtil.copy(imgFile, file);
        }
    }
}
