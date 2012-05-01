package hudson.scm;

import java.io.File;
import java.io.Serializable;

/**
 * Contains the state of the current Integrity Configuration Management Project
 */
public final class IntegrityRevisionState extends SCMRevisionState implements Serializable 
{
	private static final long serialVersionUID = 1838332506014398677L;
	private final File projectDB;

	public IntegrityRevisionState(File projectDB) 
	{
		this.projectDB = projectDB;
	}
	
	public File getProjectDB()
	{
		return projectDB;
	}
}
