// $Id: $
// (c) Copyright 2015 by PTC Inc. All rights reserved.
//
// This Software is unpublished, valuable, confidential property of
// PTC Inc. Any use or disclosure of this Software without the express
// written permission of PTC Inc. is strictly prohibited.

package hudson.scm.api.option;

import java.io.File;

import com.mks.api.FileOption;

/**
 *
 * @author Author: asen
 * @version $Revision: $
 */
public class FileAPIOption extends FileOption implements IAPIOption
{

    public FileAPIOption(String name, File value)
    {
	super(name, value);
    }

}
