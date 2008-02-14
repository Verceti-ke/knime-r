/* ------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright, 2003 - 2008
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
 *   17.09.2007 (thiel): created
 */
package org.knime.ext.r.node.local;

import org.knime.core.data.DataTableSpec;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.ext.r.node.RDialogPanel;


/**
 * A dialog containing a multi line text field to specify the R code to run.
 * 
 * @author Kilian Thiel, University of Konstanz
 */
public class RLocalScriptingNodeDialogPane extends RLocalNodeDialogPane {

    private final RDialogPanel m_dialogPanel;
    
    private static final String TAB_R_BINARY = "R Binary";
    
    /**
     * Constructor which creates a new instance of 
     * <code>RLocalScriptingNodeDialogPane</code>. A dialog component is added
     * which allows users to enter R code. 
     */
    public RLocalScriptingNodeDialogPane() {
        super();
        m_dialogPanel = new RDialogPanel();
        
        addTab("R command", m_dialogPanel);
        setDefaultTabTitle(TAB_R_BINARY);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void loadAdditionalSettingsFrom(final NodeSettingsRO settings,
            final DataTableSpec[] specs) throws NotConfigurableException {
        super.loadAdditionalSettingsFrom(settings, specs);
        m_dialogPanel.loadSettingsFrom(settings, specs);
    } 
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void saveAdditionalSettingsTo(final NodeSettingsWO settings)
        throws InvalidSettingsException {
        super.saveAdditionalSettingsTo(settings);
        m_dialogPanel.saveSettingsTo(settings);
    }    
}
