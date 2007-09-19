/* 
 * ------------------------------------------------------------------
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
 * --------------------------------------------------------------------- *
 * 
 * History
 *   19.09.2007 (gabriel): created
 */
package org.knime.ext.r.node.local;

import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * 
 * @author gabriel, University of Konstanz
 */
public final class RViewScriptingConstants {
    
    public static final HashMap<String, String> LABEL2COMMAND
        = new LinkedHashMap<String, String>();
    
    static {
        LABEL2COMMAND.put("Generic X-Y Plotting", "plot(x, y, ...)");
                
    }

    private RViewScriptingConstants() {
        
    }
    
}
