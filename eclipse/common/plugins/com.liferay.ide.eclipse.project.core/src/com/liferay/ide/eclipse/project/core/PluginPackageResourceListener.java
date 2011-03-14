/*******************************************************************************
 * Copyright (c) 2000-2011 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 *******************************************************************************/

package com.liferay.ide.eclipse.project.core;

import com.liferay.ide.eclipse.core.ILiferayConstants;
import com.liferay.ide.eclipse.core.util.CoreUtil;
import com.liferay.ide.eclipse.project.core.util.ProjectUtil;
import com.liferay.ide.eclipse.server.util.ServerUtil;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ClasspathContainerInitializer;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.core.ClasspathEntry;
import org.eclipse.jst.common.jdt.internal.classpath.FlexibleProjectContainer;
import org.eclipse.jst.j2ee.internal.J2EEConstants;
import org.eclipse.jst.j2ee.internal.common.classpath.J2EEComponentClasspathContainerUtils;
import org.eclipse.wst.common.componentcore.ComponentCore;
import org.eclipse.wst.common.componentcore.datamodel.properties.IAddReferenceDataModelProperties;
import org.eclipse.wst.common.componentcore.internal.operation.AddReferenceDataModelProvider;
import org.eclipse.wst.common.componentcore.internal.operation.RemoveReferenceDataModelProvider;
import org.eclipse.wst.common.componentcore.internal.resources.VirtualArchiveComponent;
import org.eclipse.wst.common.componentcore.resources.IVirtualComponent;
import org.eclipse.wst.common.componentcore.resources.IVirtualReference;
import org.eclipse.wst.common.frameworks.datamodel.DataModelFactory;
import org.eclipse.wst.common.frameworks.datamodel.IDataModel;
import org.eclipse.wst.common.frameworks.datamodel.IDataModelProvider;
import org.eclipse.wst.common.project.facet.core.IFacetedProject;
import org.eclipse.wst.common.project.facet.core.IProjectFacet;
import org.eclipse.wst.common.project.facet.core.IProjectFacetVersion;
import org.eclipse.wst.common.project.facet.core.ProjectFacetsManager;

/**
 * @author Greg Amerson
 */
@SuppressWarnings("restriction")
public class PluginPackageResourceListener implements IResourceChangeListener {

	public static boolean isLiferayProject(IProject project) {
		boolean retval = false;
		
		try {
			IFacetedProject facetedProject = ProjectFacetsManager.create(project);
			
			if (facetedProject != null) {	
				for (IProjectFacetVersion facet : facetedProject.getProjectFacets()) {
					IProjectFacet projectFacet = facet.getProjectFacet();
					
					if (projectFacet.getId().startsWith("liferay.")) {
						retval = true;
						
						break;						
					}
				}
			}
		}
		
		catch (CoreException e) {
		}
		
		return retval;		
	}

	public void resourceChanged(IResourceChangeEvent event) {
		if (event == null) {			
			return;			
		}

		try {
			if (shouldProcessResourceChangedEvent(event)) {
				IResourceDelta delta = event.getDelta();
				delta.accept(new IResourceDeltaVisitor() {

					public boolean visit(IResourceDelta delta)
						throws CoreException {

						try {
							int deltaKind = delta.getKind();

							if (deltaKind == IResourceDelta.REMOVED || deltaKind == IResourceDelta.REMOVED_PHANTOM) {
								return false;
							}

							if (shouldProcessResourceDelta(delta)) {
								processResourceChanged(delta);

								return false;
							}
						}
						catch (Exception e) {
							// do nothing
						}

						return delta.getResource() != null && delta.getResource().getType() != IResource.FILE;
					}
				});
			}
		}
		catch (CoreException e) {
		}
	}

	private IFile getWorkspaceFile(IPath path) {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		
		IFile file = root.getFile(path);

		if (!file.exists()) {
			file = root.getFileForLocation(path);
		}

		return file;
	}

	protected void addVirtualRef(IVirtualComponent rootComponent, IVirtualReference ref) throws CoreException {
		processVirtualRef(rootComponent, ref, new AddReferenceDataModelProvider());
	}

	protected boolean isLiferayPluginProject(IPath deltaPath) {
		IFile pluginPackagePropertiesFile = getWorkspaceFile(deltaPath);
		
		if (pluginPackagePropertiesFile != null && pluginPackagePropertiesFile.exists()) {
			return isLiferayProject(pluginPackagePropertiesFile.getProject());			
		}		
		
		return false;		
	}

	protected IVirtualReference processContext(IVirtualComponent rootComponent, String context) {
		// first check for jar file
		IFile serviceJar = ProjectUtil.findServiceJarForContext(context);

		if (serviceJar == null) {
			return null;
		}

		IPath serviceJarPath = serviceJar.getFullPath();

		if (rootComponent == null) {
			return null;
		}

		String type = VirtualArchiveComponent.LIBARCHIVETYPE + IPath.SEPARATOR;
		IVirtualComponent archive =
			ComponentCore.createArchiveComponent(rootComponent.getProject(), type +
				serviceJarPath.makeRelative().toString());

		IVirtualReference ref =
			ComponentCore.createReference(rootComponent, archive, new Path(J2EEConstants.WEB_INF_LIB).makeAbsolute());

		ref.setArchiveName(serviceJarPath.lastSegment());

		return ref;
	}

	protected void processPortalDependencyTlds(Properties props, IProject project) {
		String portalDependencyTlds = props.getProperty("portal-dependency-tlds");

		if (portalDependencyTlds != null) {
			String[] portalTlds = portalDependencyTlds.split(",");

			IVirtualComponent comp = ComponentCore.createComponent(project);
			
			if (comp != null) {
				IFolder webroot = (IFolder) comp.getRootFolder().getUnderlyingFolder();

				final IFolder tldFolder = webroot.getFolder("WEB-INF/tld");

				IPath portalDir = ServerUtil.getPortalDir(project);
				
				final List<IPath> tldFilesToCopy = new ArrayList<IPath>();
				
				for (String portalTld : portalTlds) {
					IFile tldFile = tldFolder.getFile(portalTld);
					
					if (!tldFile.exists()) {
						IPath realPortalTld = portalDir.append("WEB-INF/tld/" + portalTld);
						
						if (realPortalTld.toFile().exists()) {
							tldFilesToCopy.add(realPortalTld);
						}
					}
				}

				if (tldFilesToCopy.size() > 0) {
					new WorkspaceJob("copy portal tlds") {

						@Override
						public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {

							CoreUtil.prepareFolder(tldFolder);

							for (IPath tldFileToCopy : tldFilesToCopy) {
								IFile newTldFile = tldFolder.getFile(tldFileToCopy.lastSegment());
								
								try {
									newTldFile.create(new FileInputStream(tldFileToCopy.toFile()), true, null);
								}
								catch (FileNotFoundException e) {
									throw new CoreException(ProjectCorePlugin.createErrorStatus(e));
								}
							}

							return Status.OK_STATUS;
						}
					}.schedule();

				}
			}
		}
	}

	protected void processRequiredDeploymentContexts(Properties props, IProject project) {

		final IVirtualComponent rootComponent = ComponentCore.createComponent(project);

		if (rootComponent == null) {
			return;
		}

		final List<IVirtualReference> removeRefs = new ArrayList<IVirtualReference>();

		final IClasspathContainer webAppLibrariesContainer =
			J2EEComponentClasspathContainerUtils.getInstalledWebAppLibrariesContainer(project);

		IClasspathEntry[] existingEntries = webAppLibrariesContainer.getClasspathEntries();

		for (IClasspathEntry entry : existingEntries) {
			IPath path = entry.getPath();
			String archiveName = path.lastSegment();

			if (archiveName.endsWith("-service.jar")) {
				IFile file = getWorkspaceFile(path);

				if (file.exists() && ProjectUtil.isLiferayProject(file.getProject())) {
					for (IVirtualReference ref : rootComponent.getReferences()) {
						if (archiveName.equals(ref.getArchiveName())) {
							removeRefs.add(ref);
						}
					}
				}
			}
		}

		final List<IVirtualReference> addRefs = new ArrayList<IVirtualReference>();

		String requiredDeploymenContexts = props.getProperty("required-deployment-contexts");

		if (requiredDeploymenContexts != null) {
			String[] contexts = requiredDeploymenContexts.split(",");

			if (!CoreUtil.isNullOrEmpty(contexts)) {
				for (String context : contexts) {
					IVirtualReference ref = processContext(rootComponent, context);

					if (ref != null) {
						addRefs.add(ref);
					}
				}
			}
		}

		new WorkspaceJob("Update virtual component.") {
			@Override
			public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
				updateVirtualComponent(rootComponent, removeRefs, addRefs);
				updateWebClasspathContainer(rootComponent, addRefs);
				return Status.OK_STATUS;
			}
		}.schedule();
	}

	protected void processResourceChanged(IResourceDelta delta)
		throws CoreException {

		IPath deltaPath = delta.getFullPath();
		
		IFile pluginPackagePropertiesFile = getWorkspaceFile(deltaPath);
		
		IProject project = pluginPackagePropertiesFile.getProject();
		
		IJavaProject javaProject = JavaCore.create(project);
		
		IPath containerPath = null;
		
		IClasspathEntry[] entries = javaProject.getRawClasspath();
		
		for (IClasspathEntry entry : entries) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
				if (entry.getPath().segment(0).equals(PluginClasspathContainerInitializer.ID)) {
					containerPath = entry.getPath();
					
					break;				
				}
			}
		}
		
		if (containerPath != null) {
			IClasspathContainer classpathContainer = JavaCore.getClasspathContainer(containerPath, javaProject);
			
			PluginClasspathContainerInitializer initializer =
				(PluginClasspathContainerInitializer) JavaCore.getClasspathContainerInitializer(PluginClasspathContainerInitializer.ID);
			
			initializer.requestClasspathContainerUpdate(containerPath, javaProject, classpathContainer);
		}

		Properties props = new Properties();
		
		try {
			props.load(pluginPackagePropertiesFile.getContents());
			
			processPortalDependencyTlds(props, pluginPackagePropertiesFile.getProject());

			processRequiredDeploymentContexts(props, pluginPackagePropertiesFile.getProject());
		}
		catch (Exception e) {
			ProjectCorePlugin.logError(e);
		}

	}

	protected void processVirtualRef(IVirtualComponent rootComponent, IVirtualReference ref, IDataModelProvider provider)
		throws CoreException {

		IDataModel dm = DataModelFactory.createDataModel(provider);
		dm.setProperty(IAddReferenceDataModelProperties.SOURCE_COMPONENT, rootComponent);
		dm.setProperty(IAddReferenceDataModelProperties.TARGET_REFERENCE_LIST, Arrays.asList(ref));

		IStatus stat = dm.validateProperty(IAddReferenceDataModelProperties.TARGET_REFERENCE_LIST);

		if (stat == null || (!stat.isOK())) {
			throw new CoreException(stat);
		}

		try {
			dm.getDefaultOperation().execute(new NullProgressMonitor(), null);
		}
		catch (ExecutionException e) {
			ProjectCorePlugin.logError(e);
		}
	}

	protected void removeVirtualRef(IVirtualComponent rootComponent, IVirtualReference ref) throws CoreException {
		processVirtualRef(rootComponent, ref, new RemoveReferenceDataModelProvider());
	}

	protected boolean shouldProcessResourceChangedEvent(IResourceChangeEvent event) {
		if (event == null) {
			return false;
		}

		int eventType = event.getType();

		if (eventType != IResourceChangeEvent.POST_CHANGE) {
			return false;
		}

		IResourceDelta delta = event.getDelta();
		
		int deltaKind = delta.getKind();
		
		if (deltaKind == IResourceDelta.REMOVED || deltaKind == IResourceDelta.REMOVED_PHANTOM) {
			return false;
		}

		return true;
	}

	protected boolean shouldProcessResourceDelta(IResourceDelta delta) {
		if (delta == null) {
			return false;
		}
		
		int deltaKind = delta.getKind();

		if (deltaKind == IResourceDelta.REMOVED || deltaKind == IResourceDelta.REMOVED_PHANTOM) {
			return false;
		}

		IPath fullPath = delta.getFullPath();
		
		if (fullPath == null ||
			!(ILiferayConstants.LIFERAY_PLUGIN_PACKAGE_PROPERTIES_FILE.equals(fullPath.lastSegment()))) {
			return false;
		}

		IFile file = getWorkspaceFile(fullPath);

		if (file == null || !file.exists() || !file.isAccessible()) {
			return false;
		}

		// make sure that the file is in the docroot
		IFolder docroot = ProjectUtil.getDocroot(file.getProject());

		if (docroot != null && docroot.exists() && docroot.getFullPath().isPrefixOf(file.getFullPath())) {
			return true;
		}

		return false;
	}

	protected void updateVirtualComponent(
		IVirtualComponent rootComponent, List<IVirtualReference> removeRefs, List<IVirtualReference> addRefs)
		throws CoreException {

		for (IVirtualReference ref : removeRefs) {
			removeVirtualRef(rootComponent, ref);
		}

		for (IVirtualReference ref : addRefs) {
			addVirtualRef(rootComponent, ref);
		}
	}

	protected void updateWebClasspathContainer(IVirtualComponent rootComponent, List<IVirtualReference> addRefs)
		throws CoreException {

		IProject project = rootComponent.getProject();

		IJavaProject javaProject = JavaCore.create(project);
		FlexibleProjectContainer container =
			J2EEComponentClasspathContainerUtils.getInstalledWebAppLibrariesContainer(project);

		// If the container is present, refresh it
		if (container == null) {
			return;
		}

		container.refresh(true);

		// need to regrab this to get newest container
		container = J2EEComponentClasspathContainerUtils.getInstalledWebAppLibrariesContainer(project);

		IClasspathEntry[] webappEntries = container.getClasspathEntries();

		for (IClasspathEntry entry : webappEntries) {
			String archiveName = entry.getPath().lastSegment();

			for (IVirtualReference ref : addRefs) {
				if (ref.getArchiveName().equals(archiveName)) {
					IFile referencedFile = (IFile) ref.getReferencedComponent().getAdapter(IFile.class);

					IProject referencedFileProject = referencedFile.getProject();

					((ClasspathEntry) entry).sourceAttachmentPath =
						referencedFileProject.getFolder("docroot/WEB-INF/service").getFullPath();
				}
			}
		}

		ClasspathContainerInitializer initializer =
			JavaCore.getClasspathContainerInitializer("org.eclipse.jst.j2ee.internal.web.container");

		initializer.requestClasspathContainerUpdate(container.getPath(), javaProject, container);
	}

}
