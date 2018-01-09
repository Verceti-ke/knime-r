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
 *
 * History
 *   15.03.2012 (hofer): created
 */
package org.knime.r;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.knime.core.node.workflow.FlowVariable;

/**
 * Used by Java Snippet as a storage for flow variables during execution.
 *
 * @author Heiko Hofer
 */
public class FlowVariableRepository {
    private final Map<String, FlowVariable> m_input;

    private final Map<String, FlowVariable> m_modified;

    /**
     * Create a new repository.
     *
     * @param input the flow variables from the input stack.
     */
    public FlowVariableRepository(final Map<String, FlowVariable> input) {
        super();
        m_input = input;
        m_modified = new LinkedHashMap<String, FlowVariable>();
    }

    /**
     * Get the flow variables at the input
     *
     * @return the flow variables at the input
     */
    public Collection<FlowVariable> getInFlowVariables() {
        return m_input.values();
    }

    /**
     * Get the modified or new flow variables.
     *
     * @return the modified or new flow variables
     */
    public Collection<FlowVariable> getModified() {
        return m_modified.values();
    }

    /**
     * Get the current flow variable associated with the given name or null if a flow variable with the given name does
     * not exist.
     *
     * @param name the name of the flow variable r null if a flow variable with the given name does not exist.
     * @return the flow variable
     */
    public FlowVariable getFlowVariable(final String name) {
        final FlowVariable var = m_modified.get(name);
        return null != var ? var : m_input.get(name);
    }

    /**
     * Add a new or updated flow variable.
     *
     * @param flowVar the flow variable associated with name
     */
    public void put(final FlowVariable flowVar) {
        m_modified.put(flowVar.getName(), flowVar);
    }

}
