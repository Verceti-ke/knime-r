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
 *   19.09.2007 (thiel): created
 */
package org.knime.ext.r.node.local;

import org.knime.base.node.util.exttool.ExtToolStderrNodeView;
import org.knime.core.node.GenericNodeFactory;
import org.knime.core.node.GenericNodeView;
import org.knime.core.node.NodeDialogPane;

/**
 * Factory of the <code>RLocalViewsNodeModel</code>.
 *
 * @author Kilian Thiel, University of Konstanz
 */
public class RLocalViewsNodeFactory extends
        GenericNodeFactory<RLocalViewsNodeModel> {

    /**
     * {@inheritDoc}
     */
    @Override
    protected NodeDialogPane createNodeDialogPane() {
        return new RLocalViewsNodeDialog();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RLocalViewsNodeModel createNodeModel() {
        return new RLocalViewsNodeModel();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GenericNodeView<RLocalViewsNodeModel> createNodeView(
            final int viewIndex, final RLocalViewsNodeModel nodeModel) {
        if (viewIndex == 0) {
            return new RLocalViewsNodeView(nodeModel);
        } else if (viewIndex == 1) {
            return new ExtToolStderrNodeView<RLocalViewsNodeModel>(nodeModel);
        } else {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int getNrNodeViews() {
        return 2;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean hasDialog() {
        return true;
    }

}
