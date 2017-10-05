/*
 * ------------------------------------------------------------------------
 *  Copyright by KNIME GmbH, Konstanz, Germany
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
 */
package org.knime.ext.r.node;

import java.io.File;
import java.io.IOException;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.RowKey;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.IntCell;
import org.knime.core.data.def.StringCell;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.RFactor;
import org.rosuda.REngine.RList;
import org.rosuda.REngine.Rserve.RConnection;

/**
 * Executes R command and returns the result of the variable R as output.
 *
 * @author Thomas Gabriel, University of Konstanz
 */
public class RConsoleModel extends RRemoteNodeModel {

    private String[] m_expression = new String[]{RDialogPanel.DEFAULT_R_COMMAND};

    private static final NodeLogger LOGGER = NodeLogger.getLogger(RConsoleModel.class);

    /**
     *
     *
     */
    protected RConsoleModel() {
        super(new PortType[]{BufferedDataTable.TYPE}, new PortType[]{BufferedDataTable.TYPE});
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected PortObject[] execute(final PortObject[] inData, final ExecutionContext exec)
        throws CanceledExecutionException, Exception {
        final RConnection rconn = super.getRconnection();
        // send data to R server
        RConnectionRemote.sendData(rconn, (BufferedDataTable)inData[0], exec);
        // send expression to R server
        final String[] expression = parseExpression(m_expression);
        for (final String e : expression) {
            exec.setMessage("Executing R command: " + e);
            LOGGER.debug("voidEval: try(" + e + ")");
            rconn.voidEval("try(" + e + ")");
        }
        exec.setMessage("Recieving R result...");
        final REXP rexp = rconn.eval("try(R)");
        LOGGER.debug("R: " + rexp.toString() + " " + rexp.getClass());

        if (rexp.isString()) {
            return new BufferedDataTable[]{readStrings(rexp, exec)};
        } else if (rexp.isNumeric()) {
            try {
                return new BufferedDataTable[]{readDoubleMatrix(rexp, exec)};
            } catch (final REXPMismatchException rme) {
                if (rexp.isInteger()) {
                    return new BufferedDataTable[]{readIntegers(rexp, exec)};
                }
                return new BufferedDataTable[]{readDoubles(rexp, exec)};
            }
        } else if (rexp.isNull()) {
            return new BufferedDataTable[]{readList(rexp.asList(), exec)};
        } else if (rexp.isFactor()) {
            return new BufferedDataTable[]{readFactor(rexp.asFactor(), exec)};
        } else if (rexp.isList()) {
            return new BufferedDataTable[]{readList(rexp.asList(), exec)};
        } else if (rexp.isLogical()) {
            return new BufferedDataTable[]{readIntegers(rexp, exec)};
        } else if (rexp.isEnvironment()) {
            return new BufferedDataTable[]{readString(rexp, exec)};
        } else if (rexp.isLanguage()) {
            return new BufferedDataTable[]{readList(rexp.asList(), exec)};
        } else if (rexp.isExpression()) {
            return new BufferedDataTable[]{readList(rexp.asList(), exec)};
        } else if (rexp.isSymbol()) {
            return new BufferedDataTable[]{readStrings(rexp, exec)};
        } else if (rexp.isVector()) {
            return new BufferedDataTable[]{readString(rexp, exec)};
        } else if (rexp.isRaw()) {
            return new BufferedDataTable[]{readBytes(rexp, exec)};
        } else if (rexp.isComplex()) {
            return new BufferedDataTable[]{readString(rexp, exec)};
        } else {
            return new BufferedDataTable[]{readString(rexp, exec)};
        }
    }

    private DataTableSpec createDataTableSpec(final String[] colNames, final DataType... columnTypes) {
        final DataColumnSpec[] cspec = new DataColumnSpec[colNames.length];
        for (int i = 0; i < cspec.length; i++) {
            String name = colNames[i];
            if (name == null) {
                name = "X";
                if (i > 0) {
                    name += Integer.toString(i);
                }
            }
            final DataColumnSpecCreator colspeccreator =
                new DataColumnSpecCreator(name, columnTypes.length == 1 ? columnTypes[0] : columnTypes[i]);
            cspec[i] = colspeccreator.createSpec();
        }
        return new DataTableSpec(cspec);
    }

    private BufferedDataTable readBytes(final REXP rexp, final ExecutionContext exec)
        throws CanceledExecutionException, REXPMismatchException {
        final byte[] bytes = rexp.asBytes();
        final BufferedDataContainer dc =
            exec.createDataContainer(createDataTableSpec(new String[]{null}, StringCell.TYPE));
        for (int i = 0; i < bytes.length; i++) {
            exec.checkCanceled();
            exec.setProgress((1.0 * i) / bytes.length);
            dc.addRowToTable(new DefaultRow(RowKey.createRowKey((long)i), String.valueOf(bytes[i])));
        }
        dc.close();
        return dc.getTable();

    }

    private BufferedDataTable readList(final RList list, final ExecutionContext exec)
        throws CanceledExecutionException, REXPMismatchException {
        final Object[] object = new Object[list.size()];
        final String[] colNames = new String[object.length];
        final DataType[] colTypes = new DataType[object.length];
        int nrRows = 0;
        for (int i = 0; i < object.length; i++) {
            DataType type;
            if (list.at(i).isFactor()) {
                object[i] = list.at(i).asStrings();
                type = StringCell.TYPE;
            } else if (list.at(i).isInteger()) {
                object[i] = list.at(i).asIntegers();
                type = IntCell.TYPE;
            } else if (list.at(i).isNumeric()) {
                object[i] = list.at(i).asDoubles();
                type = DoubleCell.TYPE;
            } else {
                object[i] = list.at(i).asStrings();
                type = StringCell.TYPE;
            }
            colNames[i] = (list.names == null ? null : list.names.get(i).toString());
            colTypes[i] = type;

            nrRows = list.at(i).asStrings().length;
        }
        final DataTableSpec spec = createDataTableSpec(colNames, colTypes);
        final BufferedDataContainer dc = exec.createDataContainer(spec);
        for (int i = 0; i < nrRows; i++) {
            exec.checkCanceled();
            final DataCell[] row = new DataCell[object.length];
            for (int j = 0; j < row.length; j++) {
                if (spec.getColumnSpec(j).getType() == IntCell.TYPE) {
                    row[j] = new IntCell(((int[])object[j])[i]);
                } else if (spec.getColumnSpec(j).getType() == DoubleCell.TYPE) {
                    final double dblValue = ((double[])object[j])[i];
                    if (Double.isNaN(dblValue)) {
                        row[j] = DataType.getMissingCell();
                    } else {
                        row[j] = new DoubleCell(dblValue);
                    }
                } else {
                    final String strValue = ((String[])object[j])[i];
                    if (strValue == null) {
                        row[j] = DataType.getMissingCell();
                    } else {
                        row[j] = new StringCell(strValue);
                    }
                }
            }
            dc.addRowToTable(new DefaultRow(RowKey.createRowKey((long)i), row));
        }
        dc.close();
        return dc.getTable();
    }

    private BufferedDataTable readFactor(final RFactor fac, final ExecutionContext exec)
        throws CanceledExecutionException {
        final String[] strings = fac.asStrings();
        final BufferedDataContainer dc =
            exec.createDataContainer(createDataTableSpec(new String[]{null}, StringCell.TYPE));
        for (int i = 0; i < fac.size(); i++) {
            exec.checkCanceled();
            exec.setProgress((1.0 * i) / strings.length);
            final RowKey rowKey = RowKey.createRowKey((long)i);
            final String strValue = strings[fac.indexAt(i)];
            if (strValue == null) {
                dc.addRowToTable(new DefaultRow(rowKey, DataType.getMissingCell()));
            } else {
                dc.addRowToTable(new DefaultRow(rowKey, strValue));
            }
        }
        dc.close();
        return dc.getTable();
    }

    private BufferedDataTable readString(final REXP rexp, final ExecutionContext exec) throws REXPMismatchException {
        final String string = rexp.asString();
        final BufferedDataContainer dc =
            exec.createDataContainer(createDataTableSpec(new String[]{null}, StringCell.TYPE));
        DataRow row;
        if (string == null) {
            row = new DefaultRow(RowKey.createRowKey(0L), DataType.getMissingCell());
        } else {
            row = new DefaultRow(RowKey.createRowKey(0L), string);
        }
        dc.addRowToTable(row);
        dc.close();
        return dc.getTable();
    }

    private BufferedDataTable readDoubles(final REXP rexp, final ExecutionContext exec)
        throws REXPMismatchException, CanceledExecutionException {
        final double[] matrix = rexp.asDoubles();
        final BufferedDataContainer dc =
            exec.createDataContainer(createDataTableSpec(new String[]{null}, DoubleCell.TYPE));
        for (int i = 0; i < matrix.length; i++) {
            exec.checkCanceled();
            exec.setProgress((1.0 * i) / matrix.length);
            dc.addRowToTable(new DefaultRow(RowKey.createRowKey((long)i), matrix[i]));
        }
        dc.close();
        return dc.getTable();
    }

    private BufferedDataTable readStrings(final REXP rexp, final ExecutionContext exec)
        throws REXPMismatchException, CanceledExecutionException {
        final String[] matrix = rexp.asStrings();
        final BufferedDataContainer dc =
            exec.createDataContainer(createDataTableSpec(new String[]{null}, StringCell.TYPE));
        for (int i = 0; i < matrix.length; i++) {
            exec.checkCanceled();
            exec.setProgress((1.0 * i) / matrix.length);
            final RowKey rowKey = RowKey.createRowKey((long)i);
            if (matrix[i] == null) {
                dc.addRowToTable(new DefaultRow(rowKey, DataType.getMissingCell()));
            } else {
                dc.addRowToTable(new DefaultRow(rowKey, matrix[i]));
            }
        }
        dc.close();
        return dc.getTable();
    }

    private BufferedDataTable readDoubleMatrix(final REXP rexp, final ExecutionContext exec)
        throws REXPMismatchException, CanceledExecutionException {
        final double[][] matrix = rexp.asDoubleMatrix();
        DataTableSpec spec;
        if (matrix.length == 0) {
            spec = new DataTableSpec();
        } else {
            spec = createDataTableSpec(new String[matrix[1].length], DoubleCell.TYPE);
        }
        final BufferedDataContainer dc = exec.createDataContainer(spec);
        for (int i = 0; i < matrix.length; i++) {
            exec.checkCanceled();
            exec.setProgress((1.0 * i) / matrix.length);
            dc.addRowToTable(new DefaultRow(RowKey.createRowKey((long)i), matrix[i]));
        }
        dc.close();
        return dc.getTable();
    }

    private BufferedDataTable readIntegers(final REXP rexp, final ExecutionContext exec)
        throws REXPMismatchException, CanceledExecutionException {
        final int[] matrix = rexp.asIntegers();
        final BufferedDataContainer dc =
            exec.createDataContainer(createDataTableSpec(new String[]{null}, IntCell.TYPE));
        for (int i = 0; i < matrix.length; i++) {
            exec.checkCanceled();
            exec.setProgress((1.0 * i) / matrix.length);
            dc.addRowToTable(new DefaultRow(RowKey.createRowKey((long)i), new int[]{matrix[i]}));
        }
        dc.close();
        return dc.getTable();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
        // remove R variable
        try {
            final RConnection rconn = getRconnection();
            rconn.voidEval("try(rm(R))");
        } catch (final Throwable t) {
            LOGGER.debug("Could not remove variable R: ", t);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        return new DataTableSpec[1];
    }

    /**
     * Tests the given expressions if they contain at least one statement where data is assigned to variable R.
     *
     * @param rexps a String array containing the expression to check.
     * @throws InvalidSettingsException If the given statements do not contain at least one statement where data is
     *             assigned to variable R.
     */
    public static final void testExpressions(final String[] rexps) throws InvalidSettingsException {
        for (int i = 0; i < rexps.length; i++) {
            final String test = rexps[i].replace(" ", "");
            if (test.contains("R<-")) {
                // ok, we have an result in R
                return;
            }
        }
        throw new InvalidSettingsException("The result has to be provided as a variable R.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        super.saveSettingsTo(settings);
        RDialogPanel.setExpressionsTo(settings, m_expression);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        super.loadValidatedSettingsFrom(settings);
        m_expression = RDialogPanel.getExpressionsFrom(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        super.validateSettings(settings);
        testExpressions(RDialogPanel.getExpressionsFrom(settings));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        // empty
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        // empty
    }

}
