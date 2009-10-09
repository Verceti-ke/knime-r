/*
 * -------------------------------------------------------------------
 * Copyright, 2003 - 2009
 * University of Konstanz, Germany.
 * Chair for Bioinformatics and Information Mining
 * Prof. Dr. Michael R. Berthold
 *
 * This file is part of the R integration plugin for KNIME.
 *
 * The R integration plugin is free software; you can redistribute
 * it and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St., Fifth Floor, Boston, MA 02110-1301, USA.
 * Or contact us: contact@knime.org.
 * -------------------------------------------------------------------
 *
 */
package org.knime.ext.r.node;

import java.awt.GridLayout;

import javax.swing.JPanel;

import org.knime.core.data.DataTableSpec;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.defaultnodesettings.DialogComponentNumber;
import org.knime.core.node.defaultnodesettings.DialogComponentPasswordField;
import org.knime.core.node.defaultnodesettings.DialogComponentString;

/**
 * Panel used to login to a R server providing user, password, host, and port.
 *
 * @author Thomas Gabriel, University of Konstanz
 */
final class RLoginDialogPanel extends JPanel {

    private final DialogComponentString m_host =
            new DialogComponentString(RLoginSettings.createHostModel(),
                    "Host: ");

    private final DialogComponentNumber m_port =
            new DialogComponentNumber(RLoginSettings.createPortModel(),
                    "Port: ", 1);

    private final DialogComponentString m_user =
            new DialogComponentString(RLoginSettings.createUserModel(),
                    "User: ");;

    private final DialogComponentPasswordField m_pass =
            new DialogComponentPasswordField(RLoginSettings
                    .createPasswordModel(), "Password: ");;

    /**
     * Default constructor.
     */
    public RLoginDialogPanel() {
        super(new GridLayout(4, 1));
        super.add(m_host.getComponentPanel());
        super.add(m_port.getComponentPanel());
        super.add(m_user.getComponentPanel());
        super.add(m_pass.getComponentPanel());
    }

    /**
     * Transfers the values from the specified settings object into the dialog
     * components.
     *
     * @param settings the new settings to display in the components.
     * @param specs the table specs from the input ports.
     * @throws NotConfigurableException if settings can't be loaded
     */
    public void loadSettingsFrom(final NodeSettingsRO settings,
            final DataTableSpec[] specs) throws NotConfigurableException {
        m_host.loadSettingsFrom(settings, specs);
        m_port.loadSettingsFrom(settings, specs);
        m_user.loadSettingsFrom(settings, specs);
        m_pass.loadSettingsFrom(settings, specs);
    }

    /**
     * Saves the current values in the dialog components into the specified
     * settings object.
     *
     * @param settings the object to write the current settings into.
     * @throws InvalidSettingsException if the current values are invalid.
     */
    public void saveSettingsTo(final NodeSettingsWO settings)
            throws InvalidSettingsException {
        m_host.saveSettingsTo(settings);
        m_port.saveSettingsTo(settings);
        m_user.saveSettingsTo(settings);
        m_pass.saveSettingsTo(settings);
    }

}
