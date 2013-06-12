package org.knime.r;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.event.EventListenerList;

import org.knime.core.data.BooleanValue;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.DataValue;
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
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.workflow.FlowVariable;
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.REXPDouble;
import org.rosuda.REngine.REXPGenericVector;
import org.rosuda.REngine.REXPInteger;
import org.rosuda.REngine.REXPList;
import org.rosuda.REngine.REXPLogical;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.REXPString;
import org.rosuda.REngine.REXPVector;
import org.rosuda.REngine.REngine;
import org.rosuda.REngine.REngineException;
import org.rosuda.REngine.RList;
import org.rosuda.REngine.JRI.JRIEngine;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;

public class RController {
	NodeLogger LOGGER = NodeLogger.getLogger(RController.class);
	
	private static final String TEMP_VARIABLE_NAME = "knimertemp836481";

	private static RController instance;

	private RCommandQueue m_commandQueue;
	private RConsoleController m_consoleController;
	private JRIEngine m_engine;

	private EventListenerList listenerList;

	public static synchronized RController getDefault() {
		// TODO: recreate instance when R_HOME changes in the preferences.
		instance = instance != null ? instance : new RController();
		return instance;
	}

    // This is the standard, stable way of mapping, which supports extensive
    // customization and mapping of Java to native types.

    public interface CLibrary extends Library {
        CLibrary INSTANCE = (CLibrary)
            Native.loadLibrary((Platform.isWindows() ? "msvcrt" : "c"),
                               CLibrary.class);

        void printf(String format, Object... args);
        void setenv(String env, String value, int replace);
        int _putenv(String name);
        String getenv(String name);
    }


	private RController() {
		String rHome = org.knime.r.preferences.RPreferenceInitializer
				.getRProvider().getRHome();
		m_commandQueue = new RCommandQueue();
		m_consoleController = new RConsoleController();
		listenerList = new EventListenerList();

		try {
			if (Platform.isWindows()) {
				CLibrary.INSTANCE._putenv("R_HOME" + "=" + rHome);
				String path = CLibrary.INSTANCE.getenv("PATH");
				String rdllPath = getWinRDllPath(rHome);				
				CLibrary.INSTANCE._putenv("PATH" + "=" + path + ";" + rdllPath);
			} else {
				CLibrary.INSTANCE.setenv("R_HOME", rHome, 1);
			}
			String sysRHome = CLibrary.INSTANCE.getenv("R_HOME");
			LOGGER.info("R_HOME: " + sysRHome);
			String sysPATH = CLibrary.INSTANCE.getenv("PATH");
			LOGGER.info("PATH: " + sysPATH);
			m_engine = new JRIEngine(new String[] { "--no-save"}, m_consoleController);
		} catch (REngineException e) {
			throw new RuntimeException(e);
		}

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
	}

	/**
	 * Get path to the directory containing R.dll
	 * @param rHome the R_HOME directory
	 * @return path to the directory containing R.dll
	 */
	private String getWinRDllPath(String rHome) {
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
		if (getREngine() == null)
			throw new REngineException(null, "REngine not available");
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
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					RController.getDefault().eval(c);
				} catch (Exception e) {
				}
			}
		}).start();
	}

	public REXP timedEval(final String cmd) {
		return timedEval(cmd, 15000, true);
	}

	public REXP timedEval(final String cmd, final boolean ask) {
		return timedEval(cmd, 15000, ask);
	}

	public REXP timedEval(final String cmd, final int interval,
			final boolean ask) {
		return new MonitoredEval(interval, ask).run(cmd);
	}

	public void timedAssign(final String symbol, final REXP value) {
		timedAssign(symbol, value, 15000, true);
	}

	public void timedAssign(final String symbol, final REXP value,
			final boolean ask) {
		timedAssign(symbol, value, 15000, ask);
	}

	public void timedAssign(final String symbol, final REXP value,
			final int interval, final boolean ask) {
		new MonitoredEval(interval, ask).assign(symbol, value);
	}

	public void clearWorkspace() throws REngineException, REXPMismatchException {
		RController.getDefault().eval("rm(list = ls())");
	}

	public void exportDataValue(final DataValue value, final String name) {
		timedAssign(TEMP_VARIABLE_NAME, new REXPString(value.toString()));
		setVariableName(name);
	}
	
	public void exportFlowVariables(final Collection<FlowVariable> inFlowVariables,
			final String name, final ExecutionContext exec)
			throws REXPMismatchException {
		REXP[] content = new REXP[inFlowVariables.size()];
		String[] names = new String[inFlowVariables.size()];
		int i = 0;
		for(FlowVariable flowVar : inFlowVariables) {
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

		timedAssign(TEMP_VARIABLE_NAME, new REXPList(new RList(content, names)));
		// JGR.getREngine().assign(TEMP_VARIABLE_NAME,
		// createDataFrame(content, rexpRowNames));
		setVariableName(name);
	}	
	


	public Collection<FlowVariable> importFlowVariables(final String string,
			final ExecutionContext exec) throws REXPMismatchException, REngineException {
		List<FlowVariable> flowVars = new ArrayList<FlowVariable>();
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

		return flowVars;
	}

	public void exportDataTable(final BufferedDataTable table,
			final String name, final ExecutionContext exec)
			throws REXPMismatchException {
		DataTableSpec spec = table.getDataTableSpec();

		String[] rowNames = new String[table.getRowCount()];

		Object[] columns = initializeColumns(table);
		fillColumns(table, columns, rowNames);
		RList content = createContent(table, columns);

		REXPString rexpRowNames = new REXPString(rowNames);
		timedAssign(TEMP_VARIABLE_NAME, createDataFrame(content, rexpRowNames));
		// JGR.getREngine().assign(TEMP_VARIABLE_NAME,
		// createDataFrame(content, rexpRowNames));
		setVariableName(name);
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
			final Object[] columns, final String[] rowNames) {
		DataTableSpec spec = table.getDataTableSpec();

		int r = 0;
		for (DataRow row : table) {
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
			final Object[] columns) {
		DataTableSpec spec = table.getDataTableSpec();
		String[] colNames = spec.getColumnNames();

		RList content = new RList();

		for (int c = 0; c < spec.getNumColumns(); c++) {
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
							rList.add(new REXPString(column[i]));
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
					REXPString ri = new REXPString(column);
					content.put(colNames[c], ri);
				}
			}
		}
		return content;
	}

	public static REXP createDataFrame(final RList l, final REXP rownames)
			throws REXPMismatchException {
		if (l == null || l.size() < 1) {
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

	private void setVariableName(final String name) {
		timedEval(name + " <- " + TEMP_VARIABLE_NAME + "; rm("
				+ TEMP_VARIABLE_NAME + ")");
	}

	public BufferedDataTable importBufferedDataTable(final String string,
			final ExecutionContext exec) throws REngineException, REXPMismatchException {
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
		String[] columnTypes = eval("sapply(" + string + ",class)").asStrings();
		
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
							listCells[cc] = importCells(colValue, cc);
						}					
						cells[i] = CollectionCellFactory.createListCell(Arrays.asList(listCells));
					}
				} else {
					cells[i] = importCells(column, r);
				}
				i++;
			}
			
		    cont.addRowToTable(new DefaultRow(rowId, cells));
		}
		cont.close();
		
		return cont.getTable();
	}

	private DataCell importCells(final REXP rexp, final int r) throws REXPMismatchException {

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
			} else if (rexp.isInteger()) {
				int[] colValues = rexp.asIntegers();
				if (colValues[r] == REXPInteger.NA) {
					cells = DataType.getMissingCell();
				} else {
					cells = new IntCell(colValues[r]);
				}
			} else if (rexp.isNumeric()) {
				double[] colValues = rexp.asDoubles();
				if (colValues[r] == REXPDouble.NA 
						|| Double.isNaN(colValues[r])
					    || Double.isInfinite(colValues[r])) {
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

	public void saveWorkspace(final File tempWorkspaceFile) {
		// save workspace to file
		timedEval("save.image(\"" + tempWorkspaceFile.getAbsolutePath().replace('\\', '/') + "\");");
	}

	public void loadWorkspace(final File tempWorkspaceFile) {
		// load workspace form file
		timedEval("load(\"" + tempWorkspaceFile.getAbsolutePath().replace('\\', '/') + "\");");
	}



}

final class MonitoredEval {

	volatile boolean done;
	volatile REXP result;
	int interval;
	int checkInterval;
	boolean ask;

	public MonitoredEval(final int inter, final boolean ak) {
		done = false;
		interval = inter;
		checkInterval = interval;
		ask = ak;
	}

	protected void startMonitor() {
		int t = 0;
		while (true) {
			try {
				Thread.sleep(checkInterval);

			} catch (InterruptedException e) {
				return;
			}
			if (done) {
				return;
			}
			if (t + checkInterval < interval) {
				t = t + checkInterval;
				continue;
			}
			int cancel;
			if (ask) {
				cancel = JOptionPane
						.showConfirmDialog(
								null,
								"This R process is taking some time.\nWould you like to cancel it?",
								"Cancel R Process", JOptionPane.YES_NO_OPTION);
			} else {
				cancel = JOptionPane.YES_OPTION;
			}
			if (cancel == JOptionPane.YES_OPTION) {
				(RController.getDefault().getJRIEngine()).getRni().rniStop(0);
				return;
			} else {
				t = 0;
			}
		}
	}

	public REXP run(final String cmd) {

		try {
			if (SwingUtilities.isEventDispatchThread() && ask) {
				final String c = cmd;
				new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							result = RController.getDefault().eval(c);
						} catch (REngineException e) {
							result = null;
						} catch (REXPMismatchException e) {
							result = null;
						}
						done = true;
					}
				}).start();
				checkInterval = 10;
				startMonitor();
			} else {
				new Thread(new Runnable() {
					@Override
					public void run() {
						startMonitor();
					}
				}).start();

				result = RController.getDefault().eval(cmd);
			}
			done = true;
			return result;
		} catch (Exception e) {
			return null;
		}
	}

	public void assign(final String symbol, final REXP value) {
		if (SwingUtilities.isEventDispatchThread() && ask) {
			final String sym = symbol;
			final REXP val = value;
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						RController.getDefault().getREngine().assign(sym, val);
					} catch (REngineException e) {
						result = null;
					} catch (REXPMismatchException e) {
						result = null;
					}
					done = true;
				}
			}).start();
			checkInterval = 10;
			startMonitor();
		} else {
			new Thread(new Runnable() {
				@Override
				public void run() {
					startMonitor();
				}
			}).start();
			try {
				RController.getDefault().getREngine().assign(symbol, value);
				done = true;
			} catch (Exception e) {
			}
		}
	}
}
