/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by
 *  University of Konstanz, Germany and
 *  KNIME GmbH, Konstanz, Germany
 *  Website: http://www.knime.org; Email: contact@knime.org
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, version 2, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc.,
 *  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 * ------------------------------------------------------------------------
 *
 */
package org.knime.ext.r.node;

import java.util.ArrayList;

import org.knime.base.util.flowvariable.FlowVariableProvider;
import org.knime.base.util.flowvariable.FlowVariableResolver;
import org.knime.base.util.flowvariable.FlowVariableResolver.FlowVariableEscaper;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.port.PortType;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;

/**
 * R model to save and load login information for the R server.
 *
 * @author Thomas Gabriel, University of Konstanz
 */
abstract class RRemoteNodeModel extends NodeModel implements FlowVariableProvider {

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

    private static final String R_CONNECTION_ERROR =
        "Can't connect to R server; make sure the R server is running...";

    /** R Logger. */
    private static final NodeLogger LOGGER =
        NodeLogger.getLogger(RRemoteNodeModel.class);

    /**
     * Constructor. Specify the number of inputs and outputs required.
     * @param ins number of inputs
     * @param outs number of outputs
     */
    RRemoteNodeModel(final PortType[] ins, final PortType[] outs) {
        super(ins, outs);
    }

    /**
     * @return The connection object to Rserve.
     */
    protected final RConnection getRconnection() {
        if (System.getProperty("os.name").toLowerCase().indexOf("windows") >= 0
              && RLoginSettings.getHost().equals(RLoginSettings.DEFAULT_HOST)) {
            mSTATICRCONN = createConnection(mSTATICRCONN);
            return mSTATICRCONN;
        }
        m_rconn = createConnection(m_rconn);
        return m_rconn;
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
        LOGGER.debug("Starting R evaluation on Rserve ("
         + RLoginSettings.getHost() + ":" + RLoginSettings.getPort() + ") ...");
        RConnection rconn;
        try {
            rconn = new RConnection(RLoginSettings.getHost(),
                    RLoginSettings.getPort());
            if (rconn.needLogin()) {
                rconn.login(RLoginSettings.getUser(),
                        RLoginSettings.getPassword());
            }
        } catch (RserveException rse) {
            throw new IllegalStateException(R_CONNECTION_ERROR);
        }
        if (!rconn.isConnected()) {
            throw new IllegalStateException(R_CONNECTION_ERROR);
        }
        LOGGER.debug("R connection opened.");
        return rconn;
    }

    /**
     * Parse the given string into expressions line-by-line replacing "\r"
     * and "\t" by white spaces.
     * @param exps string commands to parse
     * @return an array of expressions for each line
     */
    protected final String[] parseExpression(final String[] exps) {
        ArrayList<String> res = new ArrayList<String>();
        for (int i = 0; i < exps.length; i++) {
            exps[i] = exps[i].replace('\r', ' ');
            exps[i] = exps[i].replace('\t', ' ');
            exps[i] = exps[i].trim();
            String help = parseLine(exps[i]);
            if (help.length() > 0) {
                String command = FlowVariableResolver.parse(help, this, new FlowVariableEscaper() {
                    @Override
                    public String readString(final FlowVariableProvider model, final String var) {
                        // R needs it in quotes
                        return "\"" + super.readString(model, var) + "\"";
                    }
                });
                res.add(command);
            }
        }
        return res.toArray(new String[0]);
    }

    private static String parseLine(final String str) {
        StringBuilder b = new StringBuilder();
        boolean isIgnoreNextChar = false;
        boolean isInQuote = false;
        for (int i = 0; i < str.length(); i++) {
            if (isIgnoreNextChar) {
                isIgnoreNextChar = false;
                b.append(str.charAt(i));
                continue;
            }
            switch (str.charAt(i)) {
            case '"':
                isInQuote = !isInQuote;
                b.append(str.charAt(i));
                break;
            case '\\':
                isIgnoreNextChar = true;
                b.append(str.charAt(i));
                break;
            case '#':
                if (!isInQuote) {
                    i = str.length();
                } else {
                    b.append(str.charAt(i));
                }
                break;
            default:
                b.append(str.charAt(i));
            }
        }
        if (isInQuote) {
            return "";
        }
        return b.toString().trim();
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
        RLoginSettings.saveSettingsTo(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        RLoginSettings.loadValidatedSettingsFrom(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        RLoginSettings.validateSettings(settings);
    }

}
