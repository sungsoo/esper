/*
 * *************************************************************************************
 *  Copyright (C) 2008 EsperTech, Inc. All rights reserved.                            *
 *  http://esper.codehaus.org                                                          *
 *  http://www.espertech.com                                                           *
 *  ---------------------------------------------------------------------------------- *
 *  The software in this package is published under the terms of the GPL license       *
 *  a copy of which has been included with this distribution in the license.txt file.  *
 * *************************************************************************************
 */

package com.espertech.esper.core.deploy;

public class ParseNodeModule extends ParseNode
{
    private String moduleName;

    public ParseNodeModule(EPLModuleParseItem item, String moduleName)
    {
        super(item);
        this.moduleName = moduleName;
    }

    public String getModuleName()
    {
        return moduleName;
    }
}