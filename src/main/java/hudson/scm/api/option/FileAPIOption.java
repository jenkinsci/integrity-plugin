/*******************************************************************************
 * Contributors:
 *     PTC 2016
 *******************************************************************************/


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
