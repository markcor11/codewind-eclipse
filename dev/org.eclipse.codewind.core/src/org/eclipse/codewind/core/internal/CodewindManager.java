/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.codewind.core.internal;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeoutException;

import org.eclipse.codewind.core.internal.InstallUtil.InstallStatus;
import org.eclipse.codewind.core.internal.connection.CodewindConnection;
import org.eclipse.codewind.core.internal.connection.CodewindConnectionManager;

public class CodewindManager {
	
	public static final String DEFAULT_CONNECTION_URI = "http://localhost:9090/"; //$NON-NLS-1$
	
	private static CodewindManager codewindManager;
	
	CodewindConnection localConnection = null;
	URI localURI = null;
	
	// Keep track of the install status and if the installer is currently running.
	// If the installer is running, this is the status that should be reported, if
	// not the install status should be reported (installerStatus will be null).
	InstallStatus installStatus = null;
	InstallerStatus installerStatus = null;
	
	public enum InstallerStatus {
		INSTALLING,
		UNINSTALLING,
		STARTING,
		STOPPING
	};
	
	private CodewindManager() {
		if (getInstallStatus(true) == InstallStatus.RUNNING) {
			createLocalConnection();
			if (localConnection != null) {
				localConnection.refreshApps(null);
			}
		}
	}

	public static CodewindManager getManager() {
		if (codewindManager == null) {
			codewindManager = new CodewindManager();
		}
		return codewindManager;
	}
	
	/**
	 * Get the current install status for Codewind
	 */
	public InstallStatus getInstallStatus(boolean update) {
		if (installStatus != null && !update) {
			return installStatus;
		}
		try {
			installStatus = InstallUtil.getInstallStatus();
			if (installStatus != InstallStatus.RUNNING) {
				removeLocalConnection();
			}
			return installStatus;
		} catch (IOException e) {
			Logger.logError("An error occurred trying to get the installer status", e); //$NON-NLS-1$
		} catch (TimeoutException e) {
			Logger.logError("Timed out trying to get the installer status", e); //$NON-NLS-1$
		}
		return InstallStatus.UNKNOWN;
	}
	
	public InstallerStatus getInstallerStatus() {
		return installerStatus;
	}
	
	public void setInstallerStatus(InstallerStatus status) {
		this.installerStatus = status;
		CoreUtil.updateAll();
	}
	
	public URI getLocalURI() {
		if (localURI == null) {
			try {
				localURI = new URI(DEFAULT_CONNECTION_URI);
			} catch (URISyntaxException e) {
				// This should not happen
				Logger.logError("Failed to create a URI from the string: " + DEFAULT_CONNECTION_URI, e); //$NON-NLS-1$
			}
		}
		return localURI;
	}
	
	public synchronized CodewindConnection getLocalConnection() {
		return localConnection;
	}

	public synchronized CodewindConnection createLocalConnection() {
		if (localConnection != null) {
			return localConnection;
		}
		try {
			CodewindConnection connection = CodewindObjectFactory.createCodewindConnection(getLocalURI());
			localConnection = connection;
			CodewindConnectionManager.add(connection);
			return connection;
		} catch(Exception e) {
			Logger.log("Attempting to connect to Codewind failed: " + e.getMessage()); //$NON-NLS-1$
		}
		return null;
	}
	
	public synchronized void removeLocalConnection() {
		if (localConnection != null) {
			localConnection.close();
			CodewindConnectionManager.removeConnection(localConnection.baseUrl.toString());
			localConnection = null;
		}
	}
	
	public void refresh() {
		for (CodewindConnection conn : CodewindConnectionManager.activeConnections()) {
			conn.refreshApps(null);
		}
	}
	
	public boolean hasActiveApplications() {
		for (CodewindConnection conn : CodewindConnectionManager.activeConnections()) {
			for (CodewindApplication app : conn.getApps()) {
				if (app.isAvailable()) {
					return true;
				}
			}
		}
		return false;
	}

}
