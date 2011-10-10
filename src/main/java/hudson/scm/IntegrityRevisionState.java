package hudson.scm;

import java.io.Serializable;

/**
 * Contains the state of the current Integrity Configuration Management Project
 */
public final class IntegrityRevisionState extends SCMRevisionState implements Serializable 
{
	private static final long serialVersionUID = 1838332506014398677L;
	private final IntegrityCMProject siProject;

	public IntegrityRevisionState(IntegrityCMProject siProject) 
	{
		this.siProject = siProject;
	}
	
	public IntegrityCMProject getSIProject()
	{
		return siProject;
	}
}
