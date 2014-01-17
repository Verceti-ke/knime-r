/* ------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright by 
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
 *   15.09.2008 (thiel): created
 */
package org.knime.ext.r.node.local;

import java.util.Map;

import org.knime.core.data.DataTableSpec;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.ext.r.node.RDialogPanel;

/**
 *
 * @author Thomas Gabriel, KNIME.com AG, Zurich
 * @since 2.7
 */
public class RLocalR2TableNodeDialogPane extends RLocalNodeDialogPane {

    private final RDialogPanel m_dialogPanel;

    /**
     * Constructor which creates a new instance of <code>RLocalR2TableNodeDialogPane</code>.
     */
    public RLocalR2TableNodeDialogPane() {
        m_dialogPanel = new RDialogPanel();
        m_dialogPanel.setText(RDialogPanel.DEFAULT_R_COMMAND);
        addTabAt(0, "R Command", m_dialogPanel);
        setSelected("R Command");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void loadAdditionalSettingsFrom(final NodeSettingsRO settings,
            final PortObjectSpec[] specs) throws NotConfigurableException {
        super.loadAdditionalSettingsFrom(settings, specs);
        Map<String, FlowVariable> flowMap = getAvailableFlowVariables();
        m_dialogPanel.loadSettingsFrom(settings, new DataTableSpec[]{}, flowMap);
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
