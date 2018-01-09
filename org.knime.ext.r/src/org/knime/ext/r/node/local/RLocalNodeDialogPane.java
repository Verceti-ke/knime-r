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
 */
package org.knime.ext.r.node.local;

import javax.swing.JFileChooser;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.defaultnodesettings.DialogComponentBoolean;
import org.knime.core.node.defaultnodesettings.DialogComponentFileChooser;
import org.knime.core.node.defaultnodesettings.DialogComponentString;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.port.PortObjectSpec;

/**
 * The <code>RLocalNodeDialogPane</code> is a dialog pane providing a file chooser to select the R executable, as well
 * as a checkbox to specify which R executable will be used to execute the R script. If the checkbox is <b>not</b>
 * checked, the R executable file specified in the KNIME-R preferences is used, if the checkbox <b>is</b> checked the
 * specified file of the file chooser dialog is used. This dialog can be extended to take use of this functionality but
 * be aware to call the super constructor when extending <code>RLocalNodeDialogPane</code>.
 *
 * @author Thomas Gabriel, University of Konstanz
 * @author Kilian Thiel, University of Konstanz
 */
public abstract class RLocalNodeDialogPane extends DefaultNodeSettingsPane {

    /** Tab name for the R binary path. */
    private static final String TAB_R_BINARY = "R Binary";

    /**
     * @return Returns a <code>SettingsModelBoolean</code> instance specifying if the determined R executable file is
     *         used.
     */
    static final SettingsModelBoolean createUseSpecifiedFileModel() {
        return new SettingsModelBoolean("R_use_specified_file", false);
    }

    private final SettingsModelBoolean m_smb;

    private final SettingsModelString m_fileModel;

    /**
     * Constructor of <code>RLocalNodeDialogPane</code> which provides a default dialog component to specify the R
     * executable file and a checkbox to specify which R executable is used.
     */
    public RLocalNodeDialogPane() {
        super();

        // create setting models and add listener to model of checkbox.
        m_fileModel = createRBinaryFile();
        m_smb = createUseSpecifiedFileModel();
        m_smb.addChangeListener(new CheckBoxChangeListener());

        // create file chooser component.
        final DialogComponentFileChooser fileChooser = new DialogComponentFileChooser(m_fileModel, "R_binarys",
            JFileChooser.OPEN_DIALOG, false, new String[]{"", ".exe"});

        setHorizontalPlacement(true);
        createNewGroup("R binary path");

        // create check box component
        final DialogComponentBoolean checkbox = new DialogComponentBoolean(m_smb, "Override default:");
        checkbox.setToolTipText("If checked, the specified file is used "
            + "as R Binary. If not checked, the file specified in " + "the KNIME's R preferences is used.");

        addDialogComponent(checkbox);
        addDialogComponent(fileChooser);

        setHorizontalPlacement(false);
        final DialogComponentString argumentsComp =
            new DialogComponentString(createRargumentsModel(), "Arguments run together with the R binary: ", false, 25);
        argumentsComp.setToolTipText("Add arguments for R;" + " --vanilla mode ensures a clean workspace.");
        addDialogComponent(argumentsComp);

        closeCurrentGroup();
        setHorizontalPlacement(false);

        enableFileChooser();
        setDefaultTabTitle(TAB_R_BINARY);
    }

    /**
     * @return a new settings model for additional R arguments per default '--vanilla' is appended
     */
    static final SettingsModelString createRargumentsModel() {
        return new SettingsModelString("R-arguments", "--vanilla");
    }

    /**
     * @return a <code>SettingsModelString</code> instance containing the path to the R executable
     */
    static final SettingsModelString createRBinaryFile() {
        final SettingsModelString sms = new SettingsModelString("R_binary_file", "");
        sms.setEnabled(false);
        return sms;
    }

    /**
     * Enable or disable file chooser model.
     *
     * {@inheritDoc}
     */
    @Override
    public void loadAdditionalSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
        throws NotConfigurableException {
        super.loadAdditionalSettingsFrom(settings, specs);
        enableFileChooser();
    }

    private class CheckBoxChangeListener implements ChangeListener {
        /** {@inheritDoc} */
        @Override
        public void stateChanged(final ChangeEvent e) {
            enableFileChooser();
        }
    }

    /**
     * Enables the file chooser model if checkbox is checked and disables it when the checkbox is not checked.
     */
    private void enableFileChooser() {
        if (m_smb.getBooleanValue()) {
            m_fileModel.setEnabled(true);
        } else {
            m_fileModel.setEnabled(false);
        }
    }

}
