package com.liferay.ide.project.core.model;

import org.eclipse.sapphire.Element;
import org.eclipse.sapphire.ElementType;
import org.eclipse.sapphire.Unique;
import org.eclipse.sapphire.Value;
import org.eclipse.sapphire.ValueProperty;
import org.eclipse.sapphire.modeling.annotations.Label;

/**
 * @author Eric Min
 */
public interface ProjectName extends Element
{

    ElementType TYPE = new ElementType( ProjectName.class );

    // *** name ***

    // @Type ( base = IProject.class)
    @Label( standard = "project name" )
    @Unique
    ValueProperty PROP_PROJECT_NAME = new ValueProperty( TYPE, "Name" );

    Value<String> getName();

    void setName( String value );

}