/*
 * ------------------------------------------------------------------------
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
 * ------------------------------------------------------------------------
 *
 * History
 *   19.09.2007 (thiel): created
 *   05.11.2015 (hale): Improved and adapted to new Rserve backend for R nodes
 */
package org.knime.ext.r.bin.preferences;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.preference.DirectoryFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.knime.ext.r.bin.Activator;
import org.knime.ext.r.bin.RBinUtil;
import org.knime.ext.r.bin.RBinUtil.InvalidRHomeException;

/**
 * Preference page for settings the R installation directory.
 *
 * @author Jonathan Hale
 * @author Heiko Hofer
 */
public class RPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {
    /**
     * Constructor
     */
    public RPreferencePage() {
        super(GRID);
    }

    @Override
    protected void createFieldEditors() {
        final DirectoryFieldEditor rHomePath =
            new DirectoryFieldEditor(RPreferenceInitializer.PREF_R_HOME, "Path to R Home", getFieldEditorParent());

        rHomePath.setPropertyChangeListener((e) -> {
            checkRVersion((String)e.getNewValue());
        });
        addField(rHomePath);

        checkRVersion(Activator.getRHOME().getAbsolutePath());
    }

    void checkRVersion(final String rHome) {
        final Path rHomePath = Paths.get(rHome);
        if (!Files.isDirectory(rHomePath)) {
            return;
        }

        try {
            RBinUtil.checkRHome(rHome);

            DefaultRPreferenceProvider prefProvider = new DefaultRPreferenceProvider(rHome);
            final Properties props = prefProvider.getProperties();
            final String version = (props.getProperty("major") + "." + props.getProperty("minor")).replace(" ", ""); // the version numbers may contain spaces

            if ("3.1.0".equals(version)) {
                setMessage("You have selected an R 3.1.0 installation. "
                    + "Please see http://tech.knime.org/faq#q26 for details.", IMessageProvider.WARNING);
            }

            if (props.getProperty("Rserve.path") == null) {
                setMessage("The package Rserve needs to be installed in your R installation.", IMessageProvider.ERROR);
            }
        } catch (InvalidRHomeException e) {
            setMessage(e.getMessage(), IMessageProvider.ERROR);
        }
    }

    @Override
    public void init(final IWorkbench workbench) {
        setPreferenceStore(Activator.getDefault().getPreferenceStore());
        setDescription("KNIME R preferences");
    }
}
