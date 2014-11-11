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
 */
package org.knime.r;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.swing.event.EventListenerList;

import org.eclipse.core.runtime.IBundleGroup;
import org.eclipse.core.runtime.IBundleGroupProvider;
import org.knime.core.data.BooleanValue;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.IntValue;
import org.knime.core.data.collection.CollectionCellFactory;
import org.knime.core.data.collection.CollectionDataValue;
import org.knime.core.data.collection.ListCell;
import org.knime.core.data.def.BooleanCell;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.IntCell;
import org.knime.core.data.def.StringCell;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.core.util.CLibrary;
import org.knime.core.util.ThreadUtils;
import org.knime.ext.r.bin.RBinUtil;
import org.knime.ext.r.bin.preferences.RPreferenceInitializer;
import org.rosuda.JRI.Rengine;
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.REXPDouble;
import org.rosuda.REngine.REXPFactor;
import org.rosuda.REngine.REXPGenericVector;
import org.rosuda.REngine.REXPInteger;
import org.rosuda.REngine.REXPList;
import org.rosuda.REngine.REXPLogical;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.REXPNull;
import org.rosuda.REngine.REXPString;
import org.rosuda.REngine.REXPVector;
import org.rosuda.REngine.REngine;
import org.rosuda.REngine.REngineException;
import org.rosuda.REngine.RFactor;
import org.rosuda.REngine.RList;
import org.rosuda.REngine.JRI.JRIEngine;

import com.sun.jna.Platform;

public class RController {

    /** ID of the feature contributing the rosuda platform libs (jri). */
	private static final String FEATURE_ID_RENGINE_R2 = "org.knime.features.rengine.r2";

    NodeLogger LOGGER = NodeLogger.getLogger(RController.class);

	private static final String TEMP_VARIABLE_NAME = "knimertemp836481";

	private static RController instance;

	private final RCommandQueue m_commandQueue;
	private final RConsoleController m_consoleController;
	private JRIEngine m_engine;

	private EventListenerList listenerList;

	private Semaphore m_lock;

	private boolean m_isRAvailable;

	private List<String> m_warnings;

	private List<String> m_errors;

	private Properties m_rProps;

	private String m_rMajorVersion;

	private String m_rMemoryLimit;

	private String m_rHome;

	private boolean m_wasRAvailable;

    static final String R_LOADED_LIBRARIES_VARIABLE = "knime.loaded.libraries";



	public static synchronized RController getDefault() {
		// TODO: recreate instance when R_HOME changes in the preferences.
		if (instance == null) {
			instance = new RController();
		} else {
			if (!instance.isRAvailable().getValue() || instance.rHomeChanged()) {
				// try to reinitialize R
				instance.initR();
			}
		}
		return instance;
	}

	private boolean rHomeChanged() {
		final String rHome = RPreferenceInitializer.getRProvider().getRHome();
		return !m_rHome.equals(rHome);
	}

	// This is the standard, stable way of mapping, which supports extensive
    // customization and mapping of Java to native types.

    private RController() {
		m_commandQueue = new RCommandQueue();
		m_consoleController = new RConsoleController();
		m_wasRAvailable = false;
		m_isRAvailable = false;
		initR();
	}


	/**
	 * @throws RuntimeException when rJava.path property of m_rProps is empty string which means that rJava is not installed.
	 */
	private void loadJRI() {
		// load jri
		String rJavaPath = m_rProps.get("rJava.path").toString().trim();
		// if rJavaPath is empty, means that rJava is not installed
		if (rJavaPath.isEmpty()) {
		  throw new RuntimeException("The KNIME R integration depends on rJava. Please use the command install.packages(\"rJava\") to install rJava in your R distribution.");
		}

		List<String> jriRelativePaths = new ArrayList<String>(3);
		if (Platform.isWindows()) {
			if (Platform.is64Bit()) {
				jriRelativePaths.add("/jri/x64/jri.dll");
			} else {
				jriRelativePaths.add("/jri/i386/jri.dll");
			}
		} else if (Platform.isLinux()) {
			jriRelativePaths.add("/jri/libjri.so");
			jriRelativePaths.add("/jri/jri.so");
		} else if (Platform.isMac()) {
			jriRelativePaths.add("/jri/libjri.jnilib");
			jriRelativePaths.add("/jri/libjri.so");
		} else {
			// Platform is not supported
			return;
		}

		StringBuilder error_msg = new StringBuilder("Cannot load jri library from any of following locations:");
		Iterator<String> iter = jriRelativePaths.iterator();
		while (iter.hasNext() && !Rengine.jriLoaded) {
		    Path path = Paths.get(rJavaPath + iter.next());
		    if (Files.exists(path)) {
    			try {
                    System.load(path.toString());
    				LOGGER.debug("Loaded jri library from " + path);
    				Rengine.jriLoaded = true;
    			} catch (UnsatisfiedLinkError e) {
    			    LOGGER.warn("Could not load jri library from '" + path + "': " + e.getMessage(), e);
    			    error_msg.append('\n').append(path).append(" (load error: ").append(e.getMessage()).append(')');
    			}
		    } else {
		        error_msg.append('\n').append(path).append(" (not found)");
		    }
		}

		if (!Rengine.jriLoaded) {
			m_errors.add(error_msg.toString());
		}
	}



	private void initR() {
		try {
			m_errors = new ArrayList<String>();
			m_warnings = new ArrayList<String>();
			if (m_wasRAvailable) {
				// FIXME: Causes KNIME To crash, workaround is now to require a restart.
//				m_engine.close();
//				m_consoleController.stop();
				m_isRAvailable = false;
				m_errors.add("You must restart KNIME in order for the changes in R to take effect.");
				return;
			}


	    	String rHome = org.knime.ext.r.bin.preferences.RPreferenceInitializer
					.getRProvider().getRHome();

	    	String rHomeCheck = RBinUtil.getDefault().checkRHome(rHome);
			if (rHomeCheck != null) {
			    m_errors.add(rHomeCheck);
				m_isRAvailable = false;
				return;
			}
			m_rHome = rHome;
			m_rProps = RBinUtil.getDefault().retrieveRProperties();

			if (!m_rProps.containsKey("major")) {
				m_errors.add("Cannot determine major version of R. Please check the R installation defined in the KNIME preferences.");
				m_isRAvailable = false;
				return;
			}

			m_rMajorVersion = m_rProps.get("major").toString().trim();
			boolean isR2RengineFeatureInstalled = isJRI_R2Installed();
			if (isR2RengineFeatureInstalled && !m_rMajorVersion.equals("2")) {
				m_errors.add("Only R in version 2.x is supported. The R installation defined in the preferences "
				        + "is of version " + m_rMajorVersion + ".x.\n"
				        + "You can fix the problem by pointing to a valid R v2 installation or by\n"
				        + " - uninstalling the feature \"" + FEATURE_ID_RENGINE_R2 + "\"\n"
				        + " - modifying your R installation and adding the package \"rJava\" (available from CRAN)\n"
				        + " - add a line to knime.ini: -Djava.library.path=<R-install-folder>/library/rJava/jri/x64\n"
				        + "   (path printed to console while running install.packages(\"rJava\") in R)\n"
				        + "and restarting KNIME.");
				m_isRAvailable = false;
				return;
			}
			m_rMemoryLimit = m_rProps.get("memory.limit").toString().trim();

			m_lock = new Semaphore(1);
			listenerList = new EventListenerList();

			CLibrary.getInstance().setenv("R_HOME", m_rHome);
			if (Platform.isWindows()) {
				String path = System.getenv("PATH");
				String rdllPath = getWinRDllPath(m_rHome);
				CLibrary.getInstance().setenv("PATH", path + ";" + rdllPath);
			}
			String sysRHome = System.getenv("R_HOME");
			LOGGER.debug("R_HOME: " + sysRHome);




			String sysPATH = System.getenv("PATH");
 			LOGGER.debug("PATH: " + sysPATH);
 			if (System.getProperty("jri.ignore.ule") == null) { // static init of Rengine.class checks this
				// Rengine should ignore when System.loadLibrary("jri") fails. We do this with System.load
 			    System.setProperty("jri.ignore.ule", "yes");
 			}
			// if System.loadLibrary("jri") was not successful
			if (!Rengine.jriLoaded) {
				loadJRI();
			}

 			final JRIEngine jriEngine = new JRIEngine(new String[] { "--no-save"}, m_consoleController);
 			if (!Rengine.jriLoaded) {
 			    throw new Exception("JRI library ('jri') not loaded. Please visit http://tech.knime.org/faq#q25 for more information.");
			}
			if (!Rengine.versionCheck()) {
			    throw new Exception("Rengine library version conflict: Rengine version="
			            + Rengine.getVersion()  + " vs. native-library-version=" + Rengine.rniGetVersion());
			}
            m_engine = jriEngine;

			// attach a thread to the console controller to get notify when
			// commands are executed via the console
			new Thread() {
				@Override
				public void run() {
					while (true) {
						// wait for r workspace change or at most given time
						try {
							m_consoleController.waitForWorkspaceChange();
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						// notify listeners
						fireWorkspaceChange();
					}
				}
			}.start();
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
			m_errors.add(e.getMessage());
			m_isRAvailable = false;
			return;
		}
		// everything is ok.
		m_isRAvailable = true;
		m_wasRAvailable = true;

		if (Platform.isWindows()) {
			try {
				// set memory to the one of the used R
				eval("memory.limit(" + m_rMemoryLimit + ");");
			} catch (REngineException e) {
				LOGGER.error("R initialisation failed.", e);
				throw new RuntimeException(e);
			} catch (REXPMismatchException e) {
				LOGGER.error("R initialisation failed.", e);
				throw new RuntimeException(e);
			}
		}
	}

	private boolean isJRI_R2Installed() {
	    for (IBundleGroupProvider provider : org.eclipse.core.runtime.Platform.getBundleGroupProviders()) {
	        for (IBundleGroup feature : provider.getBundleGroups()) {
	            String featureId = feature.getIdentifier();
	            if (FEATURE_ID_RENGINE_R2.equals(featureId)) {
	                return true;
	            }
	        }
	    }
	    return false;
	}

    /**
     * returns true when R is available and correctly initialized.
     */
    public ValueReport<Boolean> isRAvailable() {
    	return new ValueReport<Boolean>(m_isRAvailable, m_errors, m_warnings);
    }

	/**
     * see Semaphore.realease()
     */
	public void release() {
		m_lock.release();
	}

	/**
     * see Semaphore.tryAcquire()
     */
	public boolean tryAcquire() {
		return m_isRAvailable ? m_lock.tryAcquire() : false;
	}

    /**
     * see Semaphore.tryAcquire()
     */
	public boolean tryAcquire(final long timeout, final TimeUnit unit) throws InterruptedException {
		return m_isRAvailable ? m_lock.tryAcquire(timeout, unit) : false;
	}

	/**
	 * Get path to the directory containing R.dll
	 * @param rHome the R_HOME directory
	 * @return path to the directory containing R.dll
	 */
	private String getWinRDllPath(final String rHome) {
		if (Platform.is64Bit()) {
			String rdllPath64 = rHome + "\\bin\\x64";
			File rdllFile64 = new File(rdllPath64);
			if (rdllFile64.exists() && rdllFile64.isDirectory()) {
				return rdllPath64;
			} else {
				throw new RuntimeException("Cannot find path to R.dll (64bit)");
			}
		} else {
			String rdllPath32 = rHome + "\\bin\\i386";
			File rdllFile32 = new File(rdllPath32);
			if (rdllFile32.exists() && rdllFile32.isDirectory()) {
				return rdllPath32;
			} else {
				throw new RuntimeException("Cannot find path to R.dll (32bit)");
			}
		}
	}

	public JRIEngine getJRIEngine() {
		return m_engine;
	}

	public REngine getREngine() {
		return getJRIEngine();
	}

	public RCommandQueue getConsoleQueue() {
		return m_commandQueue;
	}

	public RConsoleController getConsoleController() {
		return m_consoleController;
	}

	public void addRListener(final RListener l) {
		listenerList.add(RListener.class, l);
	}

	public void removeRListener(final RListener l) {
		listenerList.remove(RListener.class, l);
	}

	protected void fireWorkspaceChange() {
		REvent e = new REvent();
		for (RListener l : listenerList.getListeners(RListener.class)) {
			l.workspaceChanged(e);
		}
	}

	public REXP idleEval(final String cmd) throws REngineException,
			REXPMismatchException {
		if (getREngine() == null) {
            throw new REngineException(null, "REngine not available");
        }
		REXP x = null;
		int lock = m_engine.tryLock();
		if (lock != 0) {
			try {
				x = m_engine.parseAndEval(cmd, null, true);
			} finally {
				m_engine.unlock(lock);
			}
		}
		return x;
	}

	public REXP eval(final String cmd) throws REngineException,
			REXPMismatchException {
		if (getREngine() == null) {
			throw new REngineException(null, "REngine not available");
		}
		REXP x = getREngine().parseAndEval(cmd, null, true);
		return x;
	}

	public void threadedEval(final String cmd) {
		final String c = cmd;
		ThreadUtils.threadWithContext(new Runnable() {
			@Override
			public void run() {
				try {
					RController.getDefault().eval(c);
				} catch (Exception e) {
				}
			}
		}).start();
	}

	public REXP monitoredEval(final String cmd, final ExecutionMonitor exec) throws CanceledExecutionException {
		try {
			return new MonitoredEval(exec).run(cmd);
		} catch (REngineException e) {
			LOGGER.error("REngine error", e);
			return new REXPNull();
		} catch (REXPMismatchException e) {
			LOGGER.error("REngine error", e);
			return new REXPNull();
		}
	}

	public void monitoredAssign(final String symbol, final REXP value, final ExecutionMonitor exec) throws CanceledExecutionException {
		try {
			new MonitoredEval(exec).assign(symbol, value);
		} catch (REngineException e) {
			LOGGER.error("REngine error", e);
		} catch (REXPMismatchException e) {
			LOGGER.error("REngine error", e);
		}

	}

	void clearWorkspace(final ExecutionMonitor exec) throws CanceledExecutionException {
	    StringBuilder b = new StringBuilder();
	    b.append("unloader <- function() {\n");
        b.append("  defaults = getOption(\"defaultPackages\")\n");
        b.append("  installed = (.packages())\n");
        b.append("  for (pkg in installed){\n");
        b.append("      if (!(as.character(pkg) %in% defaults)) {\n");
        b.append("          if(!(pkg == \"base\")){\n");
        b.append("              package_name = paste(\"package:\", as.character(pkg), sep=\"\")\n");
        b.append("              detach(package_name, character.only = TRUE)\n");
        b.append("          }\n");
        b.append("      }\n");
        b.append("  }\n");
        b.append("}\n");
        b.append("unloader();\n");
        b.append("rm(list = ls());"); // also includes the unloader function
		monitoredEval(b.toString(), exec);
	}

//	public void exportDataValue(final DataValue value, final String name) {
//		timedAssign(TEMP_VARIABLE_NAME, new REXPString(value.toString()));
//		setVariableName(name);
//	}

	public void exportFlowVariables(final Collection<FlowVariable> inFlowVariables,
			final String name, final ExecutionMonitor exec)
			throws CanceledExecutionException {
		REXP[] content = new REXP[inFlowVariables.size()];
		String[] names = new String[inFlowVariables.size()];
		int i = 0;
		for(FlowVariable flowVar : inFlowVariables) {
			exec.checkCanceled();
			names[i] = flowVar.getName();
			if (flowVar.getType().equals(FlowVariable.Type.INTEGER)) {
				content[i] = new REXPInteger(flowVar.getIntValue());
			} else if (flowVar.getType().equals(FlowVariable.Type.DOUBLE)) {
				content[i] = new REXPDouble(flowVar.getDoubleValue());
			} else { // string
				content[i] = new REXPString(flowVar.getStringValue());
			}
			i++;
		}

		monitoredAssign(TEMP_VARIABLE_NAME, new REXPList(new RList(content, names)), exec);
		// JGR.getREngine().assign(TEMP_VARIABLE_NAME,
		// createDataFrame(content, rexpRowNames));
		setVariableName(name, exec);
	}

	public Collection<FlowVariable> importFlowVariables(final String string,
			final ExecutionContext exec) {
		List<FlowVariable> flowVars = new ArrayList<FlowVariable>();
		try {
			REXP value = m_engine.get(string, null, true);

			if (value == null) {
				// A variable with this name does not exist
				return Collections.emptyList();
			}
			RList rList = value.asList();

			for (int c = 0; c < rList.size(); c++) {
				REXP rexp = rList.at(c);
				if (rexp.isInteger()) {
					flowVars.add(new FlowVariable((String)rList.names.get(c), rexp.asInteger()));
				} else if (rexp.isNumeric()) {
					flowVars.add(new FlowVariable((String)rList.names.get(c), rexp.asDouble()));
				} else if (rexp.isString()) {
					flowVars.add(new FlowVariable((String)rList.names.get(c), rexp.asString()));
				}
			}
		} catch (REngineException e) {
			LOGGER.error("Rengine error", e);
		} catch (REXPMismatchException e) {
			LOGGER.error("Rengine error", e);
		}
		return flowVars;
	}

    public List<String> importListOfLibrariesAndDelete() {
        try {
            REXP listAsREXP = eval(R_LOADED_LIBRARIES_VARIABLE);
            eval("rm(" + R_LOADED_LIBRARIES_VARIABLE + ")");
            if (!listAsREXP.isVector()) {
                return Collections.emptyList();
            } else {
                return Arrays.asList(listAsREXP.asStrings());
            }
        } catch (REXPMismatchException e) {
            LOGGER.error("Rengine error", e);
        } catch (REngineException e) {
            LOGGER.error("Rengine error", e);
        }
        return Collections.emptyList();
    }


	public void exportDataTable(final BufferedDataTable table,
			final String name, final ExecutionMonitor exec)
			throws CanceledExecutionException {
		DataTableSpec spec = table.getDataTableSpec();

		String[] rowNames = new String[table.getRowCount()];

		Object[] columns = initializeColumns(table);
		fillColumns(table, columns, rowNames, exec.createSubProgress(0.7));
		RList content = createContent(table, columns, exec.createSubProgress(0.9));

	    if (content.size() > 0) {
			REXPString rexpRowNames = new REXPString(rowNames);
			try {
				monitoredAssign(TEMP_VARIABLE_NAME, createDataFrame(content, rexpRowNames, exec), exec);
				setVariableName(name, exec);
			} catch (REXPMismatchException e) {
				LOGGER.error("Cannot create data frame with data from KNIME.", e);
			}
	    } else {
	    	try {
				eval("knime.in <- data.frame()");
			} catch (REngineException e) {
				throw new RuntimeException(e);
			} catch (REXPMismatchException e) {
				throw new RuntimeException(e);
			}
	    }
		exec.setProgress(1.0);
	}

	private Object[] initializeColumns(final BufferedDataTable table) {
		DataTableSpec spec = table.getDataTableSpec();

		Object[] columns = new Object[spec.getNumColumns()];
		for (int c = 0; c < spec.getNumColumns(); c++) {
			DataType type = spec.getColumnSpec(c).getType();
			if (type.isCollectionType()) {
				DataType elementType = type.getCollectionElementType();
				if (elementType.isCompatible(BooleanValue.class)) {
					columns[c] = new byte[table.getRowCount()][];
				} else if (elementType.isCompatible(IntValue.class)) {
					columns[c] = new int[table.getRowCount()][];
				} else if (elementType.isCompatible(DoubleValue.class)) {
					columns[c] = new double[table.getRowCount()][];
				} else {
					columns[c] = new String[table.getRowCount()][];
				}
			} else {
				if (type.isCompatible(BooleanValue.class)) {
					columns[c] = new byte[table.getRowCount()];
				} else if (type.isCompatible(IntValue.class)) {
					columns[c] = new int[table.getRowCount()];
				} else if (type.isCompatible(DoubleValue.class)) {
					columns[c] = new double[table.getRowCount()];
				} else {
					columns[c] = new String[table.getRowCount()];
				}
			}
		}
		return columns;
	}

	private void fillColumns(final BufferedDataTable table,
			final Object[] columns, final String[] rowNames, final ExecutionMonitor exec) throws CanceledExecutionException {
		DataTableSpec spec = table.getDataTableSpec();

		int r = 0;
		for (DataRow row : table) {
			exec.checkCanceled();
			exec.setProgress(r / (double)table.getRowCount());
			rowNames[r] = row.getKey().getString();
			for (int c = 0; c < spec.getNumColumns(); c++) {
				DataType type = spec.getColumnSpec(c).getType();
				DataCell cell = row.getCell(c);
				if (type.isCollectionType()) {
					if (!cell.isMissing()) {
						CollectionDataValue collValue = (CollectionDataValue) cell;
						DataType elementType = type.getCollectionElementType();
						if (elementType.isCompatible(BooleanValue.class)) {
							byte[] elementValue = new byte[collValue.size()];
							int i = 0;
							for (Iterator<DataCell> iter = collValue.iterator(); iter.hasNext();) {
								elementValue[i] = exportBooleanValue(iter.next());
								i++;
							}
							byte[][] column = (byte[][]) columns[c];
							column[r] = elementValue;

						} else if (elementType.isCompatible(IntValue.class)) {
							int[] elementValue = new int[collValue.size()];
							int i = 0;
							for (Iterator<DataCell> iter = collValue.iterator(); iter.hasNext();) {
								elementValue[i] = exportIntValue(iter.next());
								i++;
							}
							int[][] column = (int[][]) columns[c];
							column[r] = elementValue;
						} else if (elementType.isCompatible(DoubleValue.class)) {
							double[] elementValue = new double[collValue.size()];
							int i = 0;
							for (Iterator<DataCell> iter = collValue.iterator(); iter.hasNext();) {
								elementValue[i] = exportDoubleValue(iter.next());
								i++;
							}
							double[][] column = (double[][]) columns[c];
							column[r] = elementValue;
						} else {
							String[] elementValue = new String[collValue.size()];
							int i = 0;
							for (Iterator<DataCell> iter = collValue.iterator(); iter.hasNext();) {
								elementValue[i] = exportStringValue(iter.next());
								i++;
							}
							String[][] column = (String[][]) columns[c];
							column[r] = elementValue;
						}
					} else {
						// TODO: Is it correct to leave element value at null?
					}
				} else {
					if (type.isCompatible(BooleanValue.class)) {
						byte[] column = (byte[]) columns[c];
						column[r] = exportBooleanValue(cell);
					} else if (type.isCompatible(IntValue.class)) {
						int[] column = (int[]) columns[c];
						column[r] = exportIntValue(cell);
					} else if (type.isCompatible(DoubleValue.class)) {
						double[] column = (double[]) columns[c];
						column[r] = exportDoubleValue(cell);

					} else {
						String[] column = (String[]) columns[c];
						column[r] = exportStringValue(cell);

					}
				}
			}
			r++;
		}
		exec.setProgress(1.0);
	}


	private byte exportBooleanValue(final DataCell cell) {
		if (!cell.isMissing()) {
			return ((BooleanValue) cell).getBooleanValue() ? REXPLogical.TRUE
					: REXPLogical.FALSE;
		} else {
			return REXPLogical.NA;
		}
	}

	private int exportIntValue(final DataCell cell) {
		if (!cell.isMissing()) {
			return ((IntValue) cell).getIntValue();
		} else {
			return REXPInteger.NA;
		}
	}

	private double exportDoubleValue(final DataCell cell) {
		if (!cell.isMissing()) {
			return ((DoubleValue) cell).getDoubleValue();
		} else {
			return REXPDouble.NA;
		}
	}

	private String exportStringValue(final DataCell cell) {
		if (!cell.isMissing()) {
			return cell.toString();
		} else {
			return null;
		}
	}


	private RList createContent(final BufferedDataTable table,
			final Object[] columns, final ExecutionMonitor exec) throws CanceledExecutionException {
		DataTableSpec spec = table.getDataTableSpec();
		String[] colNames = spec.getColumnNames();

		RList content = new RList();

		for (int c = 0; c < spec.getNumColumns(); c++) {
			exec.checkCanceled();
			exec.setProgress(c / (double)spec.getNumColumns());
			DataType type = spec.getColumnSpec(c).getType();

			if (type.isCollectionType()) {
				DataType elementType = type.getCollectionElementType();

				if (elementType.isCompatible(BooleanValue.class)) {
					byte[][] column = (byte[][]) columns[c];
					RList rList = new RList();
					for (int i = 0; i < column.length; i++) {
						if (column[i] != null) {
							rList.add(new REXPLogical(column[i]));
						} else {
							rList.add(null);
						}
					}
					content.put(colNames[c], new REXPGenericVector(rList));
				} else if (elementType.isCompatible(IntValue.class)) {
					int[][] column = (int[][]) columns[c];
					RList rList = new RList();
					for (int i = 0; i < column.length; i++) {
						if (column[i] != null) {
							rList.add(new REXPInteger(column[i]));
						} else {
							rList.add(null);
						}
					}
					content.put(colNames[c], new REXPGenericVector(rList));
				} else if (elementType.isCompatible(DoubleValue.class)) {
					double[][] column = (double[][]) columns[c];
					RList rList = new RList();
					for (int i = 0; i < column.length; i++) {
						if (column[i] != null) {
							rList.add(new REXPDouble(column[i]));
						} else {
							rList.add(null);
						}
					}
					content.put(colNames[c], new REXPGenericVector(rList));
				} else {
					String[][] column = (String[][]) columns[c];
					RList rList = new RList();
					for (int i = 0; i < column.length; i++) {
						if (column[i] != null) {
							rList.add(createFactor(column[i]));
						} else {
							rList.add(null);
						}
					}
					content.put(colNames[c], new REXPGenericVector(rList));
				}
			} else {
				if (type.isCompatible(BooleanValue.class)) {
					byte[] column = (byte[]) columns[c];
					REXPLogical ri = new REXPLogical(column);
					content.put(colNames[c], ri);
				} else if (type.isCompatible(IntValue.class)) {
					int[] column = (int[]) columns[c];
					REXPInteger ri = new REXPInteger(column);
					content.put(colNames[c], ri);
				} else if (type.isCompatible(DoubleValue.class)) {
					double[] column = (double[]) columns[c];
					REXPDouble ri = new REXPDouble(column);
					content.put(colNames[c], ri);
				} else {
					String[] column = (String[]) columns[c];
					REXPFactor ri = createFactor(column);
					content.put(colNames[c], ri);
				}
			}
		}
		exec.setProgress(1.0);
		return content;
	}

	/** Creates factor variable efficiently (based on the implementation of
	 * {@link RFactor#RFactor(String[], int)}). Fixes bug 5576:
	 * New R nodes: String columns with many and/or long values take long to load into R.
	 * @param values non null column values
	 * @return the factor
	 */
	private static REXPFactor createFactor(final String[] values) {
	    LinkedHashMap<String, Integer> hash = new LinkedHashMap<String, Integer>();
	    int[] valueIndices = new int[values.length];
	    for (int i = 0; i < values.length; i++) {
	        int valueIndex;
	        if (values[i] == null) { // missing
	            valueIndex = REXPInteger.NA;
	        } else {
    	        Integer index = hash.get(values[i]);
                if (index == null) {
                    index = hash.size() + 1;
                    hash.put(values[i], index);
                }
                valueIndex = index.intValue();
	        }
            valueIndices[i] = valueIndex;
	    }
	    String[] levels = hash.keySet().toArray(new String[hash.size()]);
	    return new REXPFactor(valueIndices, levels);
	}

	public static REXP createDataFrame(final RList l, final REXP rownames, final ExecutionMonitor exec) throws REXPMismatchException {
		if (l == null || l.size() <= 0) {
			throw new REXPMismatchException(new REXPList(l),
					"data frame (must have dim>0)");
		}
		if (!(l.at(0) instanceof REXPVector)) {
			throw new REXPMismatchException(new REXPList(l),
					"data frame (contents must be vectors)");
		}
		return new REXPGenericVector(l, new REXPList(new RList(new REXP[] {
				new REXPString("data.frame"), new REXPString(l.keys()),
				rownames }, new String[] { "class", "names", "row.names" })));
	}

	private void setVariableName(final String name, final ExecutionMonitor exec) throws CanceledExecutionException {
		monitoredEval(name + " <- " + TEMP_VARIABLE_NAME + "; rm("
				+ TEMP_VARIABLE_NAME + ")", exec);
	}

	public BufferedDataTable importBufferedDataTable(final String string, final boolean nonNumbersAsMissing,
			final ExecutionContext exec) throws REngineException, REXPMismatchException, CanceledExecutionException {
		REXP typeRexp = eval("class(" + string + ")");
		if (typeRexp.isNull()) {
			// a variable with this name does not exist
			BufferedDataContainer cont = exec.createDataContainer(new DataTableSpec());
			cont.close();
			return cont.getTable();
		}
		String type = typeRexp.asString();
		if (!type.equals("data.frame")) {
			throw new RuntimeException("Supporting 'data.frame' as return type, only.");
		}

		// TODO: Support int[] as row names or int which defines the column of row names:
		// http://stat.ethz.ch/R-manual/R-patched/library/base/html/row.names.html
		String[] rowIds = eval("attr(" + string + " , \"row.names\")").asStrings();
		int numRows = rowIds.length;
		int ommitColumn = -1;

		REXP value = m_engine.get(string, null, true);
		RList rList = value.asList();


		DataTableSpec outSpec = createSpecFromDataFrame(rList);
		BufferedDataContainer cont = exec.createDataContainer(outSpec);
		for (int r = 0; r < numRows; r++) {
			exec.checkCanceled();
			exec.setProgress(r / (double)numRows);

			String rowId = rowIds[r];

			int numCells = ommitColumn < 0 ? rList.size() : rList.size() - 1;
			DataCell[] cells = new DataCell[numCells];
		    int i = 0;
			for (int c = 0; c < rList.size(); c++) {
				REXP column = rList.at(c);
				if (c == ommitColumn) {
					continue;
				}
				if (column.isNull()) {
					cells[i] = DataType.getMissingCell();
				} else if (column.isList()) {
					// TODO: Check before casting to REXPVector
					REXP rexp = (REXP)column.asList().get(r);
					if (rexp.isNull()) {
						cells[i] = DataType.getMissingCell();
					} else {
						REXPVector colValue = (REXPVector)rexp;
						DataCell[] listCells = new DataCell[colValue.length()];
						for (int cc = 0; cc < colValue.length(); cc++) {
							listCells[cc] = importCells(colValue, cc, nonNumbersAsMissing);
						}
						cells[i] = CollectionCellFactory.createListCell(Arrays.asList(listCells));
					}
				} else {
					cells[i] = importCells(column, r, nonNumbersAsMissing);
				}
				i++;
			}

		    cont.addRowToTable(new DefaultRow(rowId, cells));
		}
		cont.close();

		return cont.getTable();


	}

	private DataCell importCells(final REXP rexp, final int r, final boolean nonNumbersAsMissing) throws REXPMismatchException {

	     DataCell cells;

		 if (rexp.isNull()) {
				cells = DataType.getMissingCell();
			} else if (rexp.isLogical()) {
				byte[] colValues = rexp.asBytes();
				if (colValues[r] == REXPLogical.TRUE) {
					cells = BooleanCell.TRUE;
				} else if (colValues[r] == REXPLogical.FALSE) {
					cells = BooleanCell.FALSE;
				} else {
					cells = DataType.getMissingCell();
				}
			} else if (rexp.isFactor()) {
				RFactor factor = rexp.asFactor();
				String colValue = factor.at(r);
				if (colValue == null) {
					cells = DataType.getMissingCell();
				} else {
					cells = new StringCell(colValue);
				}
			} else if (rexp.isInteger()) {
				int[] colValues = rexp.asIntegers();
				if (colValues[r] == REXPInteger.NA) {
					cells = DataType.getMissingCell();
				} else {
					cells = new IntCell(colValues[r]);
				}
			} else if (rexp.isNumeric()) {
				double[] colValues = rexp.asDoubles();
				if (nonNumbersAsMissing && (REXPDouble.isNA(colValues[r])
						|| Double.isNaN(colValues[r])
					    || Double.isInfinite(colValues[r]))) {
					cells = DataType.getMissingCell();
				} else if (!nonNumbersAsMissing && REXPDouble.isNA(colValues[r])) {
					cells = DataType.getMissingCell();
				} else {
					cells = new DoubleCell(colValues[r]);
				}
			} else  {
				String[] colValues = rexp.asStrings();
				if (colValues[r] == null) {
					cells = DataType.getMissingCell();
				} else {
					cells = new StringCell(colValues[r]);
				}
			}

		return cells;
	}

	private DataTableSpec createSpecFromDataFrame(final RList rList) throws REXPMismatchException {
		List<DataColumnSpec> colSpecs = new ArrayList<DataColumnSpec>();
		for (int c = 0; c < rList.size(); c++) {
			String colName = rList.isNamed() ? rList.keyAt(c) : "R_out_" + c;
			DataType colType = null;
			REXP column = rList.at(c);
			if (column.isNull()) {
				colType = StringCell.TYPE;
			}
			if (column.isList()) {
				colType = DataType.getType(ListCell.class, DataType.getType(DataCell.class));
			} else {
				colType = importDataType(column);
			}

			colSpecs.add(new DataColumnSpecCreator(colName, colType)
					.createSpec());
		}
		return new DataTableSpec(colSpecs.toArray(new DataColumnSpec[colSpecs
				.size()]));
	}

	private DataType importDataType(final REXP column) {
		if (column.isNull()) {
			return StringCell.TYPE;
		} else if (column.isLogical()) {
			return BooleanCell.TYPE;
		} else if (column.isFactor()) {
			return StringCell.TYPE;
		} else if (column.isInteger()) {
			return IntCell.TYPE;
		} else if (column.isNumeric()) {
			return DoubleCell.TYPE;
		} else {
			return StringCell.TYPE;
		}
	}

	private String[] getObjectClasses(final String name) throws REngineException, REXPMismatchException {
		REXP rexp = eval("sapply(" + name + ",function(a)class(get(a,envir=globalenv()))[1])");
		return rexp != null && !rexp.isNull() ? rexp.asStrings() : null;
     }

	public void saveWorkspace(final File tempWorkspaceFile, final ExecutionMonitor exec) throws CanceledExecutionException {
		// save workspace to file
		monitoredEval("save.image(\"" + tempWorkspaceFile.getAbsolutePath().replace('\\', '/') + "\");", exec);
	}

    /**
     * @param tempWorkspaceFile the workspace file
     * @param exec ...
     * @return
     * @throws CanceledExecutionException
     */
    List<String> clearAndReadWorkspace(final File tempWorkspaceFile, final ExecutionMonitor exec)
        throws CanceledExecutionException {
        exec.setMessage("Clearing previous workspace");
    	clearWorkspace(exec);
    	exec.setMessage("Loading workspace");
    	monitoredEval("load(\"" + tempWorkspaceFile.getAbsolutePath().replace('\\', '/') + "\");", exec);
    	return importListOfLibrariesAndDelete();
    }

    void loadLibraries(final List<String> listOfLibraries) throws REngineException, REXPMismatchException {
        final String cmd = createLoadLibraryFunctionCall(listOfLibraries, true);
        eval(cmd);
    }

    /** A function call that loads all libraries in the argument but checking if they are not loaded yet.
     * @param listOfLibraries List of libraries from upstream node (e.g. randomForest, tree, ...)
     * @param suppressMessages if true the library call is wrapped so that no output is printed
     * @return The command string to be run in R (ends with newline)
     */
    static String createLoadLibraryFunctionCall(final List<String> listOfLibraries, final boolean suppressMessages) {
        StringBuilder functionBuilder = new StringBuilder();
        functionBuilder.append("function(packages_to_install) {\n");
        functionBuilder.append("  for (pkg in packages_to_install) {\n");
        functionBuilder.append("      if(!(pkg %in% (.packages()))) {\n");
        if (suppressMessages) {
            functionBuilder.append("          suppressMessages(library(pkg, character.only = TRUE))\n");
        } else {
            functionBuilder.append("          library(pkg, character.only = TRUE)\n");
        }
        functionBuilder.append("      }\n");
        functionBuilder.append("  }\n");
        functionBuilder.append("}\n");
        StringBuilder packageVector = new StringBuilder("c(");
        for (int i = 0; i < listOfLibraries.size(); i++) {
            packageVector.append(i == 0 ? "\"" : ", \"").append(listOfLibraries.get(i)).append("\"");
        }
        packageVector.append(")");
        return "sapply(" + packageVector + ", " + functionBuilder.toString() + ")\n";
    }
}

final class MonitoredEval {

	volatile boolean m_done;
	volatile REXP m_result;
	int m_interval;
	private final ExecutionMonitor m_exec;

	public MonitoredEval(final ExecutionMonitor exec) {
		m_done = false;
		m_interval = 300;
		m_exec = exec;
	}

	protected void startMonitor() {
		while (true) {
			try {
				Thread.sleep(m_interval);

			} catch (InterruptedException e) {
				return;
			}
			if (m_done) {
				return;
			}
			try {
				m_exec.checkCanceled();
			} catch (CanceledExecutionException e) {
				// stop R
				(RController.getDefault().getJRIEngine()).getRni().rniStop(0);
				return;
			}
		}
	}

	public REXP run(final String cmd) throws REngineException, REXPMismatchException, CanceledExecutionException {
		ThreadUtils.threadWithContext(new Runnable() {
			@Override
			public void run() {
				startMonitor();
			}
		}).start();

		m_result = RController.getDefault().eval(cmd);
		m_exec.checkCanceled();
		m_done = true;
		return m_result;
	}

	public void assign(final String symbol, final REXP value) throws REngineException, REXPMismatchException, CanceledExecutionException {
		ThreadUtils.threadWithContext(new Runnable() {
			@Override
			public void run() {
				startMonitor();
			}
		}).start();

		RController.getDefault().getREngine().assign(symbol, value);
		m_done = true;
		m_exec.checkCanceled();
	}


}

