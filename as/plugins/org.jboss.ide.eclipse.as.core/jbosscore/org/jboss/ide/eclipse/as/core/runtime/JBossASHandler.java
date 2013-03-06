/*******************************************************************************
 * Copyright (c) 2010 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.ide.eclipse.as.core.runtime;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.wst.server.core.IRuntime;
import org.eclipse.wst.server.core.IRuntimeType;
import org.eclipse.wst.server.core.IRuntimeWorkingCopy;
import org.eclipse.wst.server.core.IServer;
import org.eclipse.wst.server.core.IServerType;
import org.eclipse.wst.server.core.IServerWorkingCopy;
import org.eclipse.wst.server.core.ServerCore;
import org.jboss.ide.eclipse.as.core.JBossServerCorePlugin;
import org.jboss.ide.eclipse.as.core.Messages;
import org.jboss.ide.eclipse.as.core.runtime.DriverUtility.DriverUtilityException;
import org.jboss.ide.eclipse.as.core.server.bean.JBossServerType;
import org.jboss.ide.eclipse.as.core.server.bean.ServerBean;
import org.jboss.ide.eclipse.as.core.server.bean.ServerBeanLoader;
import org.jboss.ide.eclipse.as.core.util.IJBossToolingConstants;
import org.jboss.tools.runtime.core.JBossRuntimeLocator;
import org.jboss.tools.runtime.core.RuntimeCoreActivator;
import org.jboss.tools.runtime.core.model.AbstractRuntimeDetectorDelegate;
import org.jboss.tools.runtime.core.model.IRuntimeDetector;
import org.jboss.tools.runtime.core.model.RuntimeDefinition;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

/**
 * This class is INTERNAL and is not expected to be called or implemented directly 
 * by ANY clients!
 */
public class JBossASHandler extends AbstractRuntimeDetectorDelegate implements IJBossRuntimePluginConstants {
	
	private static String[] hasIncludedRuntimes = new String[] {SOA_P, EAP, EPP, EWP, SOA_P_STD};
	private static final String DROOLS = "DROOLS";  //$NON-NLS-1$
	private static final String ESB = "ESB"; //$NON-NLS-1$
	
	@Deprecated 
	public static final String RUNTIME_TYPES[] = IJBossToolingConstants.ALL_JBOSS_RUNTIMES;
	@Deprecated 
	public static final String SERVER_TYPES[] = IJBossToolingConstants.ALL_JBOSS_SERVERS;
	
	static {
		// See also JBIDE-12603 (fileset not added for first runtime)
		Bundle bundle = Platform.getBundle("org.jboss.ide.eclipse.archives.webtools"); //$NON-NLS-1$
		if (bundle != null) {
			try {
				if ((bundle.getState() & Bundle.INSTALLED) == 0) {
					bundle.start(Bundle.START_ACTIVATION_POLICY);
					bundle.start(Bundle.START_TRANSIENT);
				}
			} catch (BundleException e) {
				// failed, try next bundle
			}
		}
	}

	public void initializeRuntimes(List<RuntimeDefinition> runtimeDefinitions) {
		createJBossServerFromDefinitions(runtimeDefinitions);
	}
		
	public static void createJBossServerFromDefinitions(List<RuntimeDefinition> runtimeDefinitions) {
		for (RuntimeDefinition runtimeDefinition:runtimeDefinitions) {
			if (runtimeDefinition.isEnabled()) {
				File asLocation = getServerAdapterRuntimeLocation(runtimeDefinition);
				if (asLocation != null && asLocation.isDirectory()) {
					String type = runtimeDefinition.getType();
					if (serverBeanTypeExists(type)) {
						String typeId = new ServerBeanLoader(asLocation).getServerAdapterId();
						String name = runtimeDefinition.getName();
						String runtimeName = name + " " + RUNTIME; //$NON-NLS-1$
						createJBossServer(asLocation, typeId, name, runtimeName);
					}
				}
			}
			createJBossServerFromDefinitions(runtimeDefinition.getIncludedRuntimeDefinitions());
		}	
	}
	
	/*
	 * This needs to be cleaned up, but current issues are that
	 * a serverbean type's id and name are not unique. 
	 * 
	 * This method is of questionable utility.
	 */
	private static boolean serverBeanTypeExists(String type) {
		JBossServerType[] all = ServerBeanLoader.typesInOrder;
		for( int i = 0; i < all.length; i++ ) {
			if( all[i].getId().equals(type))
				return true;
		}
		return false;
	}

	private static boolean serverExistsForPath(IPath locPath) {
		IServer[] servers = ServerCore.getServers();
		for (int i = 0; i < servers.length; i++) {
			IRuntime runtime = servers[i].getRuntime();
			if(runtime != null && runtime.getLocation() != null && runtime.getLocation().equals(locPath)) {
				return true;
			}
		}
		return false;
	}
	
	private static IRuntime findRuntimeForPath(IPath locPath) {
		IRuntime[] runtimes = ServerCore.getRuntimes();
		for (int i = 0; i < runtimes.length; i++) {
			if (runtimes[i] == null || runtimes[i].getLocation() == null) {
				continue;
			}
			if (runtimes[i].getLocation().equals(locPath)) {
				return runtimes[i];
			}
		}
		return null;
	}
	
	private static void createJBossServer(File asLocation, String serverTypeId, String name, String runtimeName) {
		if (asLocation == null || !asLocation.isDirectory() || serverTypeId == null)
			return;
		IServerType serverType = ServerCore.findServerType(serverTypeId);
		if( serverType == null )
			return;
		IRuntimeType rtType = serverType.getRuntimeType();
		if( rtType == null )
			return;
		
		IPath jbossAsLocationPath = new Path(asLocation.getAbsolutePath());
		if( serverExistsForPath(jbossAsLocationPath))
			return;
		
		IRuntime runtime = findRuntimeForPath(jbossAsLocationPath);
		IProgressMonitor progressMonitor = new NullProgressMonitor();
		try {
			if (runtime == null) {
				runtime = createRuntime(runtimeName, asLocation.getAbsolutePath(), progressMonitor, rtType);
			}
			if (runtime != null) {
				createServer(progressMonitor, runtime, serverType, name);
			}
			
			if( isDtpPresent())
				new DriverUtility().createDriver(asLocation.getAbsolutePath(), serverType);
		} catch (CoreException e) {
			JBossServerCorePlugin.log(IStatus.ERROR, Messages.JBossRuntimeStartup_Cannot_create_new_JBoss_Server,e);
		} catch (DriverUtilityException e) {
			JBossServerCorePlugin.log(IStatus.ERROR, Messages.JBossRuntimeStartup_Cannott_create_new_DTP_Connection_Profile,e);
		}
	}

	private static boolean isDtpPresent() {
		String bundle1 = "org.eclipse.datatools.connectivity"; //$NON-NLS-1$
		String bundle2 = "org.eclipse.datatools.connectivity.db.generic"; //$NON-NLS-1$
		Bundle b1 = Platform.getBundle(bundle1);
		Bundle b2 = Platform.getBundle(bundle2);
		return b1 != null && b2 != null;
	}
	
	/**
	 * Creates new JBoss AS Runtime
	 * @param jbossASLocation location of JBoss AS
	 * @param progressMonitor
	 * @return runtime working copy
	 * @throws CoreException
	 */
	private static IRuntime createRuntime(String runtimeName, String jbossASLocation, 
			IProgressMonitor progressMonitor, IRuntimeType rtType) throws CoreException {
		IRuntimeWorkingCopy runtime = null;
		IPath jbossAsLocationPath = new Path(jbossASLocation);
		runtime = rtType.createRuntime(null, progressMonitor);
		runtime.setLocation(jbossAsLocationPath);
		if(runtimeName!=null) {
			runtime.setName(runtimeName);				
		}
		return runtime.save(false, progressMonitor);
	}

	/**
	 * Creates new JBoss Server
	 * @param progressMonitor
	 * @param runtime parent JBoss AS Runtime
	 * @return server working copy
	 * @throws CoreException
	 */
	private static void createServer(IProgressMonitor progressMonitor, IRuntime runtime,
			IServerType serverType, String name) throws CoreException {
		if( !serverWithNameExists(name)) {
			IServerWorkingCopy serverWC = serverType.createServer(null, null,
					new NullProgressMonitor());
			serverWC.setRuntime(runtime);
			if( name != null )
				serverWC.setName(name);
			serverWC.save(true, new NullProgressMonitor());
		}
	}
	
	private static boolean serverWithNameExists(String name) {
		IServer[] servers = ServerCore.getServers();
		for (IServer server:servers) {
			if (name.equals(server.getName()) ) {
				return true;
			}
		}
		return false;
	}
	
	public RuntimeDefinition getRuntimeDefinition(File root,
			IProgressMonitor monitor) {
		if (monitor.isCanceled() || root == null) {
			return null;
		}
		ServerBeanLoader loader = new ServerBeanLoader(root);
		ServerBean serverBean = loader.getServerBean();
		
		if (serverBean.getType() != null && !JBossServerType.UNKNOWN.equals(serverBean.getType())) {
			RuntimeDefinition runtimeDefinition = new RuntimeDefinition(serverBean.getName(), 
					serverBean.getVersion(), serverBean.getType().getId(), new File(serverBean.getLocation()));
			calculateIncludedRuntimeDefinition(runtimeDefinition, monitor);
			return runtimeDefinition;
		}
		return null;
	}
	
	private File[] getChildFolders(File root, final File ignore) {
		return root.listFiles(new FileFilter() {
			public boolean accept(File file) {
				if (!file.isDirectory() || file.equals(ignore)) {
					return false;
				}
				return true;
			}
		});
	}
	
	private void calculateIncludedRuntimeDefinition(
			RuntimeDefinition runtimeDefinition, IProgressMonitor monitor) {
		// Sanity check
		if (runtimeDefinition == null || runtimeDefinition.getType() == null ||
				!hasIncludedRuntimes(runtimeDefinition.getType())) {
			return;
		}
		
		runtimeDefinition.getIncludedRuntimeDefinitions().clear();
		File[] directories = getChildFolders(runtimeDefinition.getLocation(), getServerAdapterRuntimeLocation(runtimeDefinition));
		
		String type = runtimeDefinition.getType();
		List<RuntimeDefinition> nested = searchForNestedRuntimes(runtimeDefinition, directories, monitor);
		runtimeDefinition.getIncludedRuntimeDefinitions().addAll(nested);
		
		// Why do these types need to be specifically handled? 
		// Do the esb / drools plugin not have valid detectors?
		if (SOA_P.equals(type) || SOA_P_STD.equals(type)) {
			addDrools(runtimeDefinition);
			addEsb(runtimeDefinition);
		}
	}
	
	/*
	 * Scan through a JBossRuntimeLocator for other runtime definitions. 
	 * This allows other handlers a chance to scan the same folders, and lets
	 * us mark them as nested. 
	 */
	private List<RuntimeDefinition> searchForNestedRuntimes(RuntimeDefinition parent, File[] directoriesToSearch, IProgressMonitor monitor) {
		JBossRuntimeLocator locator = new JBossRuntimeLocator();
		ArrayList<RuntimeDefinition> defs = new ArrayList<RuntimeDefinition>();
		for (File directory : directoriesToSearch) {
			List<RuntimeDefinition> definitions = new ArrayList<RuntimeDefinition>();
			locator.searchDirectory(directory, definitions, 1, getNestedSearchRuntimeDetectors(), monitor);
			for (RuntimeDefinition definition:definitions) {
				definition.setParent(parent);
			}
			defs.addAll(definitions);
		}
		return defs;
	}
	
	/*
	 * This method must clone the core model's set, because otherwise we will be modifying 
	 * the actual model's set. Also, want to remove ourself from the list of acceptable handlers.
	 */
	private Set<IRuntimeDetector> getNestedSearchRuntimeDetectors() {
		Set<IRuntimeDetector> runtimeDetectors = RuntimeCoreActivator.getDefault().getRuntimeDetectors();
		TreeSet<IRuntimeDetector> cloned = new TreeSet<IRuntimeDetector>(runtimeDetectors);
		cloned.remove(findMyDetector());
		return cloned;
	}

	private void addDrools(RuntimeDefinition runtimeDefinition) {
		if (runtimeDefinition == null) {
			return;
		}
		Bundle drools = Platform.getBundle("org.drools.eclipse"); //$NON-NLS-1$
		Bundle droolsDetector = Platform
				.getBundle("org.jboss.tools.runtime.drools.detector");//$NON-NLS-1$
		if (drools != null && droolsDetector != null) {
			File droolsRoot = runtimeDefinition.getLocation();
			if (droolsRoot.isDirectory()) {
				String name = "Drools - " + runtimeDefinition.getName();//$NON-NLS-1$
				RuntimeDefinition droolsDefinition = new RuntimeDefinition(
						name, runtimeDefinition.getVersion(), DROOLS,
						droolsRoot);
				droolsDefinition.setParent(runtimeDefinition);
				runtimeDefinition.getIncludedRuntimeDefinitions().add(
						droolsDefinition);
			}
		}
	}
	
	private void addEsb(RuntimeDefinition runtimeDefinition) {
		if (runtimeDefinition == null) {
			return;
		}
		Bundle esb = Platform.getBundle("org.jboss.tools.esb.project.core");//$NON-NLS-1$
		Bundle esbDetectorPlugin = Platform
				.getBundle("org.jboss.tools.runtime.esb.detector");//$NON-NLS-1$
		if (esb != null && esbDetectorPlugin != null) {
			String type = runtimeDefinition.getType();
			File esbRoot;
			if (SOA_P.equals(type)) {
				esbRoot = runtimeDefinition.getLocation();
			} else {
				esbRoot = new File(runtimeDefinition.getLocation(), "jboss-esb"); //$NON-NLS-1$
			}
			if (esbRoot.isDirectory()) {
				String name = "ESB - " + runtimeDefinition.getName();//$NON-NLS-1$
				String version="";//$NON-NLS-1$
				RuntimeDefinition esbDefinition = new RuntimeDefinition(
						name, version, ESB,
						esbRoot);
				IRuntimeDetector esbDetector = RuntimeCoreActivator.getDefault().getEsbDetector();
				if (esbDetector != null) {
					version = esbDetector.getVersion(esbDefinition);
					esbDefinition.setVersion(version);
				}
				
				esbDefinition.setParent(runtimeDefinition);
				runtimeDefinition.getIncludedRuntimeDefinitions().add(
						esbDefinition);
			}
		}
	}

	private boolean hasIncludedRuntimes(String type) {
		return Arrays.asList(hasIncludedRuntimes).contains(type);
	}

	private String getLocationForRuntimeDefinition(RuntimeDefinition runtimeDefinition) {
		String path = null;
		if (runtimeDefinition != null && runtimeDefinition.getLocation() != null) {
			File location = getServerAdapterRuntimeLocation(runtimeDefinition);
			if (location != null && location.isDirectory()) {
				try {
					path = location.getCanonicalPath();
				} catch (IOException e) {
					JBossServerCorePlugin.log(e);
					path = location.getAbsolutePath();
				}
			}
		}
		return path;
	}
	
	private static File getServerAdapterRuntimeLocation(RuntimeDefinition runtimeDefinitions) {
		ServerBeanLoader loader = new ServerBeanLoader( runtimeDefinitions.getLocation() );
		String version = runtimeDefinitions.getVersion();
		String relative = loader.getServerBean().getType().getRootToAdapterRelativePath(version);
		if( relative == null )
			return runtimeDefinitions.getLocation();
		return new File(runtimeDefinitions.getLocation(), relative);
	}
	
	@Override
	public boolean exists(RuntimeDefinition runtimeDefinition) {
		// Does a wtp-style runtime with this location already exist?
		String path = getLocationForRuntimeDefinition(runtimeDefinition);
		if (path != null) {
			IServer[] servers = ServerCore.getServers();
			for (int i = 0; i < servers.length; i++) {
				IRuntime runtime = servers[i].getRuntime();
				if (runtime != null && runtime.getLocation() != null) {
					String loc = runtime.getLocation().toOSString();
					try {
						loc = new File(loc).getCanonicalPath();
					} catch (IOException e) {
						JBossServerCorePlugin.log(e);
					}
					if(path.equals(loc)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	@Override
	public void computeIncludedRuntimeDefinition(RuntimeDefinition runtimeDefinition) {
		calculateIncludedRuntimeDefinition(runtimeDefinition, new NullProgressMonitor());
	}

	@Override
	public String getVersion(RuntimeDefinition runtimeDefinition) {
		return runtimeDefinition.getVersion();
	}
	
}
