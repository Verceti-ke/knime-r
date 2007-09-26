/* 
 * -------------------------------------------------------------------
 * Copyright, 2003 - 2007
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

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;

/**
 * R model to save and load login information for the R server.
 * 
 * @author Thomas Gabriel, University of Konstanz
 */
abstract class RNodeModel extends NodeModel {
    
    /** 
     * Used only in cases where Rserve runs on local host and windows in order
     * to overcome the problem that only one connection can be open at the 
     * time. 
     */ 
    private static RConnection mSTATICRCONN;
    
    /**
     * R connection for all non-windows machines.
     */
    private RConnection m_rconn;
    private final RLoginSettings m_login = new RLoginSettings();
    
    /** R Logger. */
    private static final NodeLogger LOGGER = 
        NodeLogger.getLogger(RNodeModel.class);
    
    /**
     * Constructor. Specify the number of inputs and outputs required.
     * @param dataIns number of inputs.
     * @param dataOuts number of outputs.
     */
    RNodeModel(final int dataIns, final int dataOuts) {
        super(dataIns, dataOuts);
    }
    
    /**
     * @return The connection object to Rserve.
     */
    protected final RConnection getRconnection() {
        if (System.getProperty("os.name").toLowerCase().indexOf("windows") >= 0
                && m_login.getHost().equals(RLoginSettings.DEFAULT_HOST)) {
            return mSTATICRCONN = createConnection(mSTATICRCONN);
        } else {
            return m_rconn = createConnection(m_rconn);
        }
        
    }
    
    private RConnection createConnection(final RConnection checkR) {
        if (checkR != null && checkR.isConnected()) {
            try {
                checkR.eval("try()");
                return checkR;
            } catch (RserveException e) {
                LOGGER.debug("Exception during try(): ", e);
            }
        }
        if (checkR != null) {
            checkR.close();
        }
        LOGGER.info("Starting R evaluation on Rserve (" 
                + m_login.getHost() + ":" + m_login.getPort() + ") ...");
        RConnection rconn;
        try {
            rconn = new RConnection(m_login.getHost(), m_login.getPort());
            if (rconn.needLogin()) {
                rconn.login(m_login.getUser(), m_login.getPassword());
            }
        } catch (RserveException rse) {
            LOGGER.error("Can't connect to server");
            throw new IllegalStateException("Make sure R Server is "
                    + "available before executing this node");
        }
        if ((rconn == null) || (!rconn.isConnected())) {
            LOGGER.error("Can't connect to server");
            throw new IllegalStateException("Make sure R Server is "
                    + "available before executing this node");
        }
        LOGGER.debug("R connection opened");
        return rconn;
    }
    
    /**
     * Reset R connection.
     */
    @Override
    protected void reset() {
        if (mSTATICRCONN != null) {
            mSTATICRCONN.close();
            mSTATICRCONN = null;
        }
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        m_login.saveSettingsTo(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        m_login.loadValidatedSettingsFrom(settings);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings) 
            throws InvalidSettingsException {
        m_login.validateSettings(settings);
    }
}
