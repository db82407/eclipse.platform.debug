/*****************************************************************
 * Copyright (c) 2009 Texas Instruments and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Patrick Chuong (Texas Instruments) - Initial API and implementation (Bug 238956)
 *****************************************************************/
package org.eclipse.debug.internal.ui.views.breakpoints;

import org.eclipse.debug.internal.ui.viewers.model.provisional.IPresentationContext;
import org.eclipse.jface.viewers.IStructuredSelection;

/**
 * This interface can be implemented by a breakpoint manager content provider to provides filtering support.
 * 
 * @since 3.6
 */
public interface IBreakpointFilterListener {
	
	/**
	 * Sets the filter selection for the given input, the selection is the new selection of the debug view.
	 *  
	 * @param input the view input.
	 * @param context the presentation context.
	 * @param ss the selection.
	 */
	void setFilterSelection(Object input, IPresentationContext context, IStructuredSelection ss);
	
}
