package hudson.scm.localclient;

import hudson.model.User;
import hudson.scm.ChangeLogSet;

import java.io.Serializable;
import java.util.Collection;

/**
 * Created by asen on 19-06-2017.
 */
public class IntegrityLcChangeSet extends ChangeLogSet.Entry implements
                Serializable
{
    private final String messageStart = "msg";
    private final String fileStart = "file";
    private String msg;
    private String file;
    private final String splitOperator = ":";
    private final String tokenOperator = ",";
    private String author = "user";

    public IntegrityLcChangeSet(String line)
    {
        String[] tokens = line.split(tokenOperator);
        for (String token: tokens) {
            String[] split = token.trim().split(splitOperator);
            if (split[0].startsWith(messageStart)) {
                this.msg = split[1].trim();
            } else if (split[0].startsWith(fileStart)) {
                this.file = split[1].trim();
            } else {
                this.msg = "Invalid Field Found in Change Log : "+token;
            }
        }
    }

    @Override
    public String getMsg()
    {
	return msg;
    }

    @Override
    public User getAuthor()
    {
        if (author == null)
        {
            return User.getUnknown();
        }
        return User.get(author);
    }

    public String getFile()
    {
	return file;
    }

    @Override
    public Collection<String> getAffectedPaths()
    {
	return null;
    }

    @Override
    protected void setParent(ChangeLogSet parent)
    {
        super.setParent(parent);
    }
}
