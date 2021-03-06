/*******************************************************************************
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
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
 *******************************************************************************/

package com.liferay.ide.xml.search.ui.markerResolutions;

import com.liferay.ide.project.core.ProjectCore;
import com.liferay.ide.project.core.ValidationPreferences;
import com.liferay.ide.server.util.ComponentUtil;
import com.liferay.ide.xml.search.ui.LiferayXMLSearchUI;
import com.liferay.ide.xml.search.ui.XMLSearchConstants;

import java.net.URL;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IMarkerResolution2;


/**
 * @author Kuo Zhang
 */
public class DecreaseInstanceScopeXmlValidationLevel implements IMarkerResolution2
{

    private final static String MESSAGE = "Disable this type of validation in all projects";

    public DecreaseInstanceScopeXmlValidationLevel()
    {
    }

    @Override
    public String getDescription()
    {
        return MESSAGE;
    }

    @Override
    public Image getImage()
    {
        final URL url = LiferayXMLSearchUI.getDefault().getBundle().getEntry( "/icons/arrow_down.png" );
        return ImageDescriptor.createFromURL( url ).createImage();
    }

    @Override
    public String getLabel()
    {
        return MESSAGE;
    }

    @Override
    public void run( IMarker marker )
    {
        // if the project scope is used, set its validation level to "Ignore" first.
        final IEclipsePreferences node =
            new ProjectScope( marker.getResource().getProject() ).getNode( ProjectCore.PLUGIN_ID );

        final String validationKey = marker.getAttribute( XMLSearchConstants.VALIDATION_KEY, null );
        if( validationKey != null )
        {
            if( node.getBoolean( ProjectCore.USE_PROJECT_SETTINGS, false ) )
            {
                ValidationPreferences.setProjectScopeValidationLevel(
                    marker.getResource().getProject(), validationKey, -1 );
            }

            ValidationPreferences.setInstanceScopeValidationLevel( validationKey, -1 );
            ComponentUtil.validateFile( (IFile) marker.getResource(), new NullProgressMonitor() );
        }
    }

}
