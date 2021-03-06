/******************************************************************************* 
 * Copyright (c) 2010 - 2011 Red Hat, Inc. 
 * Distributed under license by Red Hat, Inc. All rights reserved. 
 * This program is made available under the terms of the 
 * Eclipse Public License v1.0 which accompanies this distribution, 
 * and is available at http://www.eclipse.org/legal/epl-v10.html 
 * 
 * Contributors: 
 * Red Hat, Inc. - initial API and implementation 
 ******************************************************************************/ 
package org.jboss.ide.eclipse.as.test.publishing.v2;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.wst.common.frameworks.datamodel.IDataModel;
import org.eclipse.wst.server.core.IModule;
import org.eclipse.wst.server.core.ServerUtil;
import org.jboss.ide.eclipse.as.test.publishing.AbstractDeploymentTest;
import org.jboss.ide.eclipse.as.test.util.ServerRuntimeUtils;
import org.jboss.ide.eclipse.as.test.util.wtp.JavaEEFacetConstants;
import org.jboss.ide.eclipse.as.test.util.wtp.OperationTestCase;
import org.jboss.ide.eclipse.as.test.util.wtp.ProjectCreationUtil;

public class JSTDeployBinaryChildModuleTest extends AbstractJSTDeploymentTester {
	protected String getModuleName() {
		return super.getModuleName() + "JSTDeployBinaryChild";
	}
	
	@Override
	protected IProject createProject() throws Exception {
		IDataModel dm = ProjectCreationUtil.getWebDataModel(getModuleName(), null, null, null, null, JavaEEFacetConstants.WEB_24, false);
		OperationTestCase.runAndVerify(dm);
		IProject p = ResourcesPlugin.getWorkspace().getRoot().getProject(getModuleName());
		assertTrue(p.exists());
		File srcFile = AbstractDeploymentTest.getFileLocation("projectPieces/EJB3NoDescriptor.jar");
		String proj = p.getLocation().toOSString();
		p.getFolder("WebContent").getFolder("WEB-INF")
			.getFolder("lib").getFile("test.jar").create(
				new FileInputStream(srcFile), true, new NullProgressMonitor());
		p.refreshLocal(0, new NullProgressMonitor());
		return p;
	}

	public void testStandardBinaryChildDeployment() throws CoreException, IOException {
		IModule mod = ServerUtil.getModule(project);
		IModule[] module = new IModule[] { mod };
		verifyJSTPublisher(module);
		server = ServerRuntimeUtils.addModule(server, mod);
		ServerRuntimeUtils.publish(server);
		IPath deployRoot = new Path(ServerRuntimeUtils.getDeployRoot(server));
		IPath rootFolder = deployRoot.append(getModuleName() + ".war");
		assertTrue(rootFolder.toFile().exists());
		IPath webinf_lib_testjar = rootFolder.append("WEB-INF").append("lib").append("test.jar");
		assertTrue("test.jar exists in deployment", webinf_lib_testjar.toFile().exists());
		assertTrue("test.jar File is actually a file", webinf_lib_testjar.toFile().isFile());
	}
	
	public void testStandardBinaryChildDeploymentMockPublishMethod() throws CoreException, IOException {
		server = ServerRuntimeUtils.useMockPublishMethod(server);
		MockPublishMethod.reset();
		testJBoss7BinaryChildDeployment(8);
	}

	public void testJBoss7BinaryChildDeployment() throws CoreException, IOException {
		server = ServerRuntimeUtils.createMockJBoss7Server();
		server = ServerRuntimeUtils.useMockPublishMethod(server);
		MockPublishMethod.reset();
		testJBoss7BinaryChildDeployment(9);
	}

	private void testJBoss7BinaryChildDeployment(int count) throws CoreException, IOException  {
		IModule mod = ServerUtil.getModule(project);
		IModule[] module = new IModule[] { mod };
		server = ServerRuntimeUtils.addModule(server, mod);
		ServerRuntimeUtils.publish(server);
		assertEquals(count,MockPublishMethod.getChanged().length);
		MockPublishMethod.reset();
	}

}
