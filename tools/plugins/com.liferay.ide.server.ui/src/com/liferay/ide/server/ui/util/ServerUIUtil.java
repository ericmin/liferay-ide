/**
 * Copyright (c) 2014 Liferay, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of the End User License
 * Agreement for Liferay Developer Studio ("License"). You may not use this file
 * except in compliance with the License. You can obtain a copy of the License
 * by contacting Liferay, Inc. See the License for the specific language
 * governing permissions and limitations under the License, including but not
 * limited to distribution rights of the Software.
 */

package com.liferay.ide.server.ui.util;

import com.liferay.ide.core.util.CoreUtil;
import com.liferay.ide.ui.LiferayUIPlugin;

import org.eclipse.jface.preference.IPreferenceStore;

/**
 * @author Terry Jia
 */
public class ServerUIUtil
{

    private static final String EXPLORER = "explorer";
    private static final String FINDER = "open";
    private static final String LINUX_FILE_MANAGER = "linuxFileManager";
    private static final String LINUX_FILE_MANAGER_PATH = "linuxFileManagerPath";
    private static final String OTHER = "other";

    public static String getSystemExplorerCommand()
    {
        String explorerCommand = "";

        if( CoreUtil.isWindows() )
        {
            explorerCommand = EXPLORER;
        }
        else if( CoreUtil.isLinux() )
        {
            IPreferenceStore store = LiferayUIPlugin.getDefault().getPreferenceStore();

            explorerCommand = store.getString( LINUX_FILE_MANAGER );

            if( explorerCommand.equals( OTHER ) )
            {
                explorerCommand = store.getString( LINUX_FILE_MANAGER_PATH );
            }
        }
        else if( CoreUtil.isMac() )
        {
            explorerCommand = FINDER;
        }

        return explorerCommand;
    }

}
