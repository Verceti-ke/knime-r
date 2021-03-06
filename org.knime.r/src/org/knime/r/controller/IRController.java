/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General  License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General  License for more details.
 *
 *  You should have received a copy of the GNU General  License
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
 * ---------------------------------------------------------------------
 *
 * History
 *   09.11.2015 (Jonathan Hale): created
 */
package org.knime.r.controller;

import java.io.File;
import java.util.Collection;
import java.util.List;

import org.knime.core.data.MissingCell;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.core.node.workflow.NodeContext;
import org.knime.core.util.ThreadUtils;
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.Rserve.RConnection;

/**
 * Interface for RController.
 *
 * @author Jonathan Hale
 */
public interface IRController extends AutoCloseable {

    /**
     * Marker class for exceptions during R configuration or execution.
     *
     * @author Jonathan Hale, KNIME, Konstanz, Germany
     */
    public static class RException extends Exception {
        /** Generated serialVersionUID */
        private static final long serialVersionUID = -167928539964071316L;

        /** Message constants */
        public static String MSG_EVAL_FAILED = "R evaluation failed.";

        /**
         * Constructor
         * 
         * @param msg Message
         * @param cause Cause (parent exception)
         */
        public RException(final String msg, final Throwable cause) {
            super(msg, cause);
        }
    }

    /**
     * Marker class for exception occurring when trying to use RController when not initialized.
     *
     * @author Jonathan Hale
     */
    public static class RControllerNotInitializedException extends RuntimeException {
        /** Generated serialVersionUID */
        private static final long serialVersionUID = 3788368549686421509L;

        /**
         * Constructor
         */
        public RControllerNotInitializedException() {
            super("CODING PROBLEM\tRController was not initalized at this point.");
        }
    }

    /**
     * Initialize everything which may fail. This includes starting up servers/checking for libraries etc.
     *
     * @throws RException
     */
    void initialize() throws RException;

    /**
     * @return <code>true</code> if {@link #initialize()} was called on this instance and {@link #close()} has not been
     *         called since.
     */
    boolean isInitialized();

    /**
     * Set whether to use {@link NodeContext}s for Threads.
     *
     * @param useNodeContext Whether to use node contexts
     * @see ThreadUtils#threadWithContext(Runnable)
     * @see ThreadUtils#threadWithContext(Runnable, String)
     * @see IRController#isUsingNodeContext()
     */
    void setUseNodeContext(boolean useNodeContext);

    /**
     * @return <code>true</code> if currently using NodeContexts in threads.
     * @see IRController#setUseNodeContext(boolean)
     */
    boolean isUsingNodeContext();

    /**
     * @return The underlying REngine (usually an {@link RConnection})
     */
    RConnection getREngine();

    /**
     * Evaluate R code. This may have side effects on the workspace of the RController.
     *
     * @param expr R expression
     * @param cmd the R expression to evaluate
     * @param resolve Whether to resolve the resulting reference, if <code>false</code> null returned (unless an
     *            exception is thrown)
     * @return result of evaluation.
     * @throws RException
     */
    REXP eval(String expr, boolean resolve) throws RException;

    /**
     * Evaluate R code in a separate thread to be able to cancel it.
     *
     * @param cmd The R command
     * @param exec only used for checking if execution is cancelled.
     * @param resolve Whether to resolve the resulting reference
     * @return Result of the evaluation, either a reference (if resolve is false) or the resolved value.
     * @throws RException
     * @throws CanceledExecutionException
     * @throws InterruptedException
     */
    REXP monitoredEval(String cmd, ExecutionMonitor exec, boolean resolve)
        throws RException, CanceledExecutionException, InterruptedException;

    /**
     * Assign a String to an R variable.
     *
     * @param expr Expression to assign the value to. Usually a variable name
     * @param value Value to assign
     * @throws RException
     */
    void assign(String expr, String value) throws RException;

    /**
     * Assign an REXP an R variable.
     *
     * @param expr Expression to assign the value to. Usually a variable name
     * @param value Value to assign
     * @throws RException
     */
    void assign(String expr, REXP value) throws RException;

    /**
     * Assign an R variable in a separate thread to be able to cancel it.
     *
     * @param symbol R variable name
     * @param value REXP value to assign to the variable
     * @param exec Execution Monitor
     * @throws RException
     * @throws CanceledExecutionException
     * @throws InterruptedException
     * @see #monitoredEval(String, ExecutionMonitor, boolean)
     * @see #assign(String, REXP)
     */
    void monitoredAssign(String symbol, REXP value, ExecutionMonitor exec)
        throws RException, CanceledExecutionException, InterruptedException;

    /**
     * Clear the R workspace (remove all variables and imported packages).
     *
     * @param exec Execution Monitor
     * @throws RException
     * @throws CanceledExecutionException
     */
    void clearWorkspace(ExecutionMonitor exec) throws RException, CanceledExecutionException;

    /**
     * @param workspaceFile R workspace file to read
     * @param tempWorkspaceFile the workspace file
     * @param exec execution monitor to report progress on
     * @return List of libraries which were previously imported in the workspace. See
     *         {@link #importListOfLibrariesAndDelete()}.
     * @throws RException
     * @throws CanceledExecutionException
     */
    List<String> clearAndReadWorkspace(final File workspaceFile, final ExecutionMonitor exec)
        throws RException, CanceledExecutionException;

    /**
     * Write R variables into a R variable in the current workspace
     *
     * @param inFlowVariables Flow variables to export into the R workspace
     * @param name Name for the R variable to contain the flow variables
     * @param exec Execution monitor
     * @throws RException
     * @throws CanceledExecutionException
     */
    void exportFlowVariables(Collection<FlowVariable> inFlowVariables, String name, ExecutionMonitor exec)
        throws RException, CanceledExecutionException;

    /**
     * Get flow variables from a R variable.
     *
     * @param variableName Name of the variable to get the {@link FlowVariable}s from.
     * @return The extracted flow variables.
     * @throws RException If an R related error occurred during execution.
     */
    Collection<FlowVariable> importFlowVariables(String variableName) throws RException;

    /**
     * Assign a {@link BufferedDataTable} to a R variable in the current workspace.
     *
     * @param symbol R variable to assign to.
     * @param value The table to assign to the variable.
     * @param exec For monitoring the progress.
     * @param batchSize max number of rows to send to R per batch.
     * @param type R type for "symbol" to provide the table data as
     * @param sendRowNames Whether to send names of rows to R with the input table
     * @throws RException If an R related error occurred during execution.
     * @throws CanceledExecutionException If execution was cancelled.
     * @throws InterruptedException If a thread was interrupted.
     */
    void monitoredAssign(String symbol, BufferedDataTable value, ExecutionMonitor exec, int batchSize, String type,
        boolean sendRowNames) throws RException, CanceledExecutionException, InterruptedException;

    /**
     * Import a BufferedDataTable from the R expression <code>string</code>.
     *
     * @param string R expression (variable for e.g.) to retrieve a data.frame which is then converted into a
     *            BufferedDataTable.
     * @param nonNumbersAsMissing Convert NaN and Infinity to {@link MissingCell}.
     * @param exec Execution context for creating the table and monitoring execution.
     * @return The created BufferedDataTable.
     * @throws RException
     * @throws CanceledExecutionException
     */
    BufferedDataTable importBufferedDataTable(String string, boolean nonNumbersAsMissing, ExecutionContext exec)
        throws RException, CanceledExecutionException;

    /**
     * Get list of libraries imported in the current session and then delete those imports.
     *
     * @return The list of deleted imports
     * @throws RException
     */
    List<String> importListOfLibrariesAndDelete() throws RException;

    /**
     * Save the workspace in the current R session to the specified file.
     *
     * @param workspaceFile File to save the workspace to.
     * @param exec For monitoring the progress.
     * @throws RException If an R related error occurred during execution.
     * @throws CanceledExecutionException If execution was cancelled.
     */
    void saveWorkspace(File workspaceFile, ExecutionMonitor exec) throws RException, CanceledExecutionException;

    /**
     * Import RInputPorts and BufferedDataTables into the current R workspace.
     *
     * @param inData ports to import
     * @param exec For monitoring the progess.
     * @param batchSize max number of rows to send to R per batch.
     * @param rType R type for "symbol" to provide the table data as
     * @param sendRowNames Whether to send row names of input tables to R
     * @throws RException
     * @throws CanceledExecutionException
     */
    void importDataFromPorts(PortObject[] inData, ExecutionMonitor exec, final int batchSize, final String rType,
        final boolean sendRowNames) throws RException, CanceledExecutionException;

}
