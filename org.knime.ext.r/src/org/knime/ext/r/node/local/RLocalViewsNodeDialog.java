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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.knime.core.node.defaultnodesettings.DialogComponentColumnFilter;
import org.knime.core.node.defaultnodesettings.DialogComponentMultiLineString;
import org.knime.core.node.defaultnodesettings.DialogComponentStringSelection;
import org.knime.core.node.defaultnodesettings.SettingsModelFilterString;
import org.knime.core.node.defaultnodesettings.SettingsModelString;

/**
 * The dialog of the <code>RLocalViewsNodeDialog</code> which provides a
 * drop down menu containing a set of names of R plots and a multi line text 
 * field to specify R code for the usage of plots. When a certain R plot
 * is specified using the drop down menu, a dummy R code template is
 * shown up in the multi line text field. Additionally a column selection panel 
 * is provided to specify columns to use and import into R.
 * 
 * @author Kilian Thiel, University of Konstanz
 */
public class RLocalViewsNodeDialog extends RLocalNodeDialogPane {

    /**
     * @return a <code>SettingsModelString</code> instance containing
     * a set of names of R views.
     */
    static final SettingsModelString createViewSettingsModel() {
        return new SettingsModelString("R_View", 
                RViewScriptingConstants.LABEL2COMMAND.keySet()
                .toArray()[0].toString());
    }
    
    /**
     * @return a <code>SettingsModelFilterString</code> instance 
     * containing the columns to use.
     */
    static final SettingsModelFilterString createColFilterSettingsModel() {
        return new SettingsModelFilterString("R_Cols");
    }
    
    /**
     * @return a <code>SettingsModelString</code> instance 
     * containing the R plot code.
     */
    static final SettingsModelString createRViewCmdSettingsModel() {
        return new SettingsModelString("R-View_command", 
                RViewScriptingConstants.LABEL2COMMAND.get(
                        RLocalViewsNodeDialog.createViewSettingsModel()
                        .getStringValue()));
    }
    
    
    
    private final SettingsModelString m_viewCommandModel;
    
    private final SettingsModelString m_viewSettingsModel;
    
    /**
     * Creates new instance of <code>RLocalViewsNodeDialog</code>.
     */
    public RLocalViewsNodeDialog() {
        super();
                
        Set<String> keys = RViewScriptingConstants.LABEL2COMMAND.keySet();
        List<String> list = new ArrayList<String>(keys);
        
        
        createNewGroup("R view");
        
        m_viewSettingsModel = createViewSettingsModel();
        addDialogComponent(new DialogComponentStringSelection(
                m_viewSettingsModel, "View type ", list));
        m_viewSettingsModel.addChangeListener(new ViewChangeListener());
        
        m_viewCommandModel = createRViewCmdSettingsModel(); 
        addDialogComponent(new DialogComponentMultiLineString(
                m_viewCommandModel, "R command", true, 10, 10));
        
        closeCurrentGroup();
        
        addDialogComponent(new DialogComponentColumnFilter(
                createColFilterSettingsModel(), 0));
    }
    
    /**
     * Listener to react on selection changes made in the drop down menu.
     * 
     * @author Kilian Thiel, University of Konstanz
     */
    class ViewChangeListener implements ChangeListener {

        /**
         * Shows up the related dummy code of the chosen R view 
         * in the multi line text field when the selection of drop down menu 
         * changes. 
         * 
         * {@inheritDoc}
         */
        public void stateChanged(final ChangeEvent e) {
            m_viewCommandModel.setStringValue(
                    RViewScriptingConstants.LABEL2COMMAND.get(
                            m_viewSettingsModel.getStringValue()));
        }
    }
}
