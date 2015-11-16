package hudson.scm;

import hudson.FilePath;
import hudson.model.TaskListener;

import java.io.File;
import java.io.IOException;

import rpc.IntegrityException;

public class TestIntegrityRepo {
	protected String name; // The name of this repository.
	protected TaskListener listener;
	
	/**
     * This is where the commit commands create a Git repository.
     */
	public File IntegrityDir; // was "workDir"
	public FilePath IntegrityDirPath; // was "workspace"
	public final String userName1="john";
	public String userName2="johnny";
    
    public TestIntegrityRepo(String name, File tmpDir, TaskListener listener) throws IOException, InterruptedException
    {
		this.name = name;
		this.listener = listener;			
		IntegrityDir = tmpDir;	

		// initialize the git interface.
		IntegrityDirPath = new FilePath(IntegrityDir);
		//git = Git.with(listener, envVars).in(gitDir).getClient();

        // finally: initialize the repo
		//git.init();
	}
	
      /**
     * Creates a commit in current repo.
     * @param fileName relative path to the file to be commited with the given content
     * @param fileContent content of the commit
     * @param author author of the commit
     * @param committer committer of this commit
     * @param message commit message
     * @return SHA1 of latest commit
     * @throws GitException
     * @throws InterruptedException
     */
    public String commit(final String fileName, final String User1, final String message)
            throws IntegrityException, InterruptedException {
        return commit(fileName,User1, message);
    }
    public String commit(final String fileName, final String fileContent, final String User1,
                         final String message) throws IntegrityException, InterruptedException {
        FilePath file = IntegrityDirPath.child(fileName);
        try {
            file.write(fileContent, null);
        } catch (Exception e) {
            throw new IntegrityException("unable to write file");
        }
        return fileName;
    }

}
