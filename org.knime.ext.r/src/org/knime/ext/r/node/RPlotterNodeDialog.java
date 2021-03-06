/*
 * ------------------------------------------------------------------------
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
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
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
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
 * ------------------------------------------------------------------------
 *
 */
package org.knime.ext.r.node;

import java.util.Map;

import org.knime.core.data.DataTableSpec;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.ext.r.node.local.RViewsDialogPanel;
import org.knime.ext.r.node.local.RViewsPngDialogPanel;

/**
 * Dialog of the R plotter to select two numeric columns.
 *
 * @author Thomas Gabriel, University of Konstanz
 */
public class RPlotterNodeDialog extends RNodeDialogPane {

    private final RViewsDialogPanel m_plotCommandPanel;

    private final RViewsPngDialogPanel m_viewPngPanel;

    /**
     * New pane for configuring REvaluator node dialog.
     */
    protected RPlotterNodeDialog() {
        super();
        m_plotCommandPanel = new RViewsDialogPanel();
        super.addTab("R View Command", m_plotCommandPanel);
        m_viewPngPanel = new RViewsPngDialogPanel();
        super.addTab(RViewsPngDialogPanel.TAB_PNG_TITLE, m_viewPngPanel, false);
        super.addLoginTab();
    }

    /**
     * Calls the update method of the underlying filter panel using the input data table spec from this
     * <code>FilterColumnNodeModel</code>.
     *
     * @param settings The <code>NodeSettings</code> to read from.
     * @param specs The input specs.
     * @throws NotConfigurableException If no columns in spec.
     */
    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final DataTableSpec[] specs)
        throws NotConfigurableException {
        super.loadSettingsFrom(settings, specs);
        final Map<String, FlowVariable> flowMap = getAvailableFlowVariables();
        m_plotCommandPanel.loadSettings(settings, specs, flowMap);
        m_viewPngPanel.loadSettings(settings, specs);
    }

    /**
     * Sets the list of columns to exclude inside the underlying <code>FilterColumnNodeModel</code> retrieving them from
     * the filter panel.
     * 
     * @param settings The <code>NodeSettings</code> to write into.
     * @throws InvalidSettingsException If column list does not contain two items.
     * @see org.knime.core.node.NodeDialogPane#saveSettingsTo( NodeSettingsWO)
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        super.saveSettingsTo(settings);
        m_plotCommandPanel.saveSettings(settings);
        m_viewPngPanel.saveSettings(settings);
    }
}
