/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/


package org.eclipse.debug.internal.ui.views.updatePolicy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.internal.ui.DebugUIMessages;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.jface.util.ListenerList;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IPersistable;
import org.eclipse.ui.WorkbenchException;
import org.eclipse.ui.XMLMemento;

public class UpdatePolicyMgr implements IUpdatePolicyManager {

	private static final IUpdatePolicySet[] EMPTY = new IUpdatePolicySet[0];
	private static final String ELEMENT_POLICY_SET = "policySet"; //$NON-NLS-1$
	private static final String ELEMENT_POLICY = "policy"; //$NON-NLS-1$
	
	private static final String POLICY_SET_MEMENTO_TYPE = "policySet"; //$NON-NLS-1$
	private static final String POLICY_SET_ROOT = "policySetRoot"; //$NON-NLS-1$
	private static final String POLICY_SET_FILE = "policySets.xml"; //$NON-NLS-1$
	
	private static final int ADDED = 0;
	private static final int REMOVED = 1;
	private static final int CHANGED = 2;
	
	private static IUpdatePolicyManager fgDefault;
	
	private List fPolicySets = new ArrayList();
	private Hashtable fPolicies = new Hashtable();
	private ListenerList fListeners = new ListenerList();
	
	class NotifyListenersRunnable implements ISafeRunnable
	{
		private IUpdatePolicySetListener fListener;
		private int  fEvent;
		private IUpdatePolicySet fSet;
		
		NotifyListenersRunnable(IUpdatePolicySetListener listener, int event, IUpdatePolicySet policySet)
		{
			fEvent = event;
			fListener = listener;
			fSet = policySet;
		}

		public void handleException(Throwable exception) {
			DebugUIPlugin.log(exception);
		}

		public void run() throws Exception {
			switch (fEvent) {
			case ADDED:
				fListener.policySetAdded(fSet);
				break;
			case REMOVED:
				fListener.policySetRemoved(fSet);
				break;
			case CHANGED:
				fListener.policySetChanged(fSet);
				break;
			default:
				break;
			}
		}
	}
	
	public static IUpdatePolicyManager getDefault()
	{
		if (fgDefault == null)
			fgDefault = new UpdatePolicyMgr();
		
		return fgDefault;
	}
	
	public UpdatePolicyMgr()
	{
		parseExtensionPoint();
		readPolicySetsFromFile();
	}
	
	public IUpdatePolicySet[] getPolicySets(String viewId, String modelId) {
		List returnList = new ArrayList();
		
		Iterator iter = fPolicySets.iterator();
		while(iter.hasNext())
		{
			UpdatePolicySet policySet = (UpdatePolicySet)iter.next();
			if (policySet.isApplicable(viewId, modelId))
				returnList.add(policySet);
		}
		
		if (returnList.isEmpty())
			return EMPTY;
		
		return (IUpdatePolicySet[])returnList.toArray(new IUpdatePolicySet[returnList.size()]);
	}
	
	public IUpdatePolicySet getPrimaryPolicySet(String viewId, String modelId)
	{
		IUpdatePolicySet[] policySets = getPolicySets(viewId, modelId);
		
		// only one policy set specified, returned as primary
		if (policySets.length == 1)
			return policySets[0];
		
		// find the primary one
		for (int i=0; i<policySets.length; i++)
		{
			if (policySets[i].isPrimary())
				return policySets[i];
		}
		
		// none is specified as primary, return the first one found
		if (policySets.length > 0)
			return policySets[0];
		
		return null;
	}
	
	private void parseExtensionPoint()
	{
        IExtensionPoint extensionPoint= Platform.getExtensionRegistry().getExtensionPoint(DebugUIPlugin.getUniqueIdentifier(), IUpdatePolicyConstants.EXTENSION_POINT_UPDATE_POLICY);
        IConfigurationElement[] configurationElements = extensionPoint.getConfigurationElements();
        for (int i = 0; i < configurationElements.length; i++) {
            IConfigurationElement element= configurationElements[i];
            if (element.getName().equals(ELEMENT_POLICY_SET))
            {
            	try {
					UpdatePolicySet policySet = new UpdatePolicySet(element);
					policySet.validate();
					fPolicySets.add(policySet);
				} catch (CoreException e) {
					DebugUIPlugin.log(e);
				}
            }
            else if (element.getName().equals(ELEMENT_POLICY))
            {
            	try {
					UpdatePolicy policy = new UpdatePolicy(element);
					policy.validate();
					fPolicies.put(policy.getId(), policy);
				} catch (CoreException e) {
					DebugUIPlugin.log(e);
				}
            }
        }
	}

	public IUpdatePolicy getPolicy(String policyId) {
		return (IUpdatePolicy) fPolicies.get(policyId);
	}

	public IUpdatePolicySet getPolicySet(String id) {
		Iterator iter = fPolicySets.iterator();
		while(iter.hasNext())
		{
			UpdatePolicySet policySet = (UpdatePolicySet)iter.next();
			if (policySet.getId().equals(id))
				return policySet;
		}
		return null;
	}

	public void addPolicySet(IUpdatePolicySet policySet) throws DebugException{
		
		validatePolicySet(policySet);
		
		Iterator iter = fPolicySets.iterator();
		
		while (iter.hasNext())
		{
			IUpdatePolicySet existingSet = (IUpdatePolicySet)iter.next();
			if (existingSet.getId().equals(policySet.getId()))
			{
				IStatus status = DebugUIPlugin.newErrorStatus(DebugUIMessages.UpdatePolicyMgr_0, null);
				throw new DebugException(status);
			}
		}
		
		fPolicySets.add(policySet);
		notifyListeners(policySet, ADDED);
	}
	
	private void validatePolicySet(IUpdatePolicySet policySet)throws DebugException
	{
		if (policySet.getId() == null)
		{
			IStatus status = DebugUIPlugin.newErrorStatus(DebugUIMessages.UpdatePolicyMgr_1, null);
			throw new DebugException(status);
		}
		
		if (!policySet.isHidden())
		{
			if (policySet.getName() == null)
			{
				IStatus status = DebugUIPlugin.newErrorStatus(DebugUIMessages.UpdatePolicyMgr_2, null);
				throw new DebugException(status);			
			}
			
			if (policySet.getDescription() == null)
			{
				IStatus status = DebugUIPlugin.newErrorStatus(DebugUIMessages.UpdatePolicyMgr_3, null);
				throw new DebugException(status);
			}
		}
	}

	public void removePolicySet(IUpdatePolicySet policySet) throws DebugException{
		if (!policySet.canRemove())
		{
			IStatus status = DebugUIPlugin.newErrorStatus(DebugUIMessages.UpdatePolicyMgr_4, null);
			throw new DebugException(status);
		}
		
		fPolicySets.remove(policySet);
		notifyListeners(policySet, REMOVED);
	}

	public IUpdatePolicySet[] getAllPolicySets() {
		return (IUpdatePolicySet[])fPolicySets.toArray(new IUpdatePolicySet[fPolicySets.size()]);
	}

	public void addPolicySetListener(IUpdatePolicySetListener listener) {
		fListeners.add(listener);
	}

	public void removePolicySetListener(IUpdatePolicySetListener listener) {
		fListeners.remove(listener);	
	}

	public void policySetChanged(IUpdatePolicySet policySet) {
		notifyListeners(policySet, CHANGED);
	}

	private void notifyListeners(IUpdatePolicySet set, int event)
	{
		Object[] listeners = fListeners.getListeners();
		for (int i=0; i<listeners.length; i++)
		{
			if (listeners[i] instanceof IUpdatePolicySetListener)
			{
				NotifyListenersRunnable runnable = new NotifyListenersRunnable((IUpdatePolicySetListener)listeners[i],  event, set);
				Platform.run(runnable);
			}
		}
	}

	public IUpdatePolicy[] getAllPolicies() {
		return (IUpdatePolicy[])fPolicies.values().toArray(new IUpdatePolicy[fPolicies.values().size()]);
	}
	
	public void shutdown()
	{
		savePolicySetsToFile();
	}
	
	public void savePolicySets(IMemento memento) {	
		// save debug models
		Iterator iter = fPolicySets.iterator();
		while (iter.hasNext())
		{
			IUpdatePolicySet set = (IUpdatePolicySet)iter.next();
			
			if (set instanceof UpdatePolicySet)
			{
				UpdatePolicySet policySet = (UpdatePolicySet)set;
				if (policySet.isUserDefined())
				{
					IMemento node= memento.createChild(POLICY_SET_MEMENTO_TYPE);
					((IPersistable)policySet).saveState(node);
				}
			}
		}
	}
	
	protected void restorePolicySets(IMemento memento)
	{
		if (memento == null)
			return;
		
		// TODO:  should make use of IPersistableElement and its factory
		IMemento[] children = memento.getChildren(POLICY_SET_MEMENTO_TYPE);
		for (int i=0; i<children.length; i++){
			UpdatePolicySet set = UpdatePolicySet.create(children[i]);
			if (set != null)
				fPolicySets.add(set);
		}
	}

	public void savePolicySetsToFile()
	{
		XMLMemento memento = XMLMemento.createWriteRoot(POLICY_SET_ROOT);
		savePolicySets(memento);

		IPath file = getDataFile();
		File fileHandle = new File(file.toOSString());
		if (!fileHandle.exists())
		{
			try {
				fileHandle.createNewFile();
			} catch (IOException e) {
				DebugUIPlugin.log(e);
				return;
			}
		}
		String fileName = file.toOSString();
		try {
			FileOutputStream stream = new FileOutputStream(fileName);
			OutputStreamWriter writer = new OutputStreamWriter(stream, "utf-8"); //$NON-NLS-1$
			memento.save(writer);
			writer.close();
			stream.close();
		} catch (FileNotFoundException e) {
			DebugUIPlugin.log(e);
		} catch (UnsupportedEncodingException e) {
			DebugUIPlugin.log(e);
		} catch (IOException e) {
			DebugUIPlugin.log(e);
		}
	}
	
	public void readPolicySetsFromFile()
	{
		IPath file = getDataFile();
		File fileHandle = new File(file.toOSString());
		if (!fileHandle.exists())
		{
			return;
		}
		
		try {
			FileInputStream stream = new FileInputStream(fileHandle);
			InputStreamReader reader = new InputStreamReader(stream, "utf-8"); //$NON-NLS-1$
			XMLMemento memento = XMLMemento.createReadRoot(reader);
			restorePolicySets(memento);
			
		} catch (FileNotFoundException e) {
			DebugUIPlugin.log(e);
		} catch (UnsupportedEncodingException e) {
			DebugUIPlugin.log(e);
		} catch (WorkbenchException e) {
			DebugUIPlugin.log(e);
		}
	}
	
	protected IPath getDataFile()
	{
		String filename = POLICY_SET_FILE;
		IPath path = DebugUIPlugin.getDefault().getStateLocation();
		path = path.addTrailingSeparator();
		
		File dirPath = new File(path.toOSString());
		if (!dirPath.exists())
			dirPath.mkdir();
		
		path = path.append(filename);
		
		return path;
	}	
}
