package hudson.scm.localclient;

import java.util.Collection;
import java.util.Collections;

import hudson.model.User;
import hudson.scm.ChangeLogSet;

/**
 * Created by asen on 19-06-2017.
 */
public class IntegrityLcChangeSet extends ChangeLogSet.Entry
{
    private static final String messageStart = "msg";
    private static final String fileStart = "file";
    private static final String contextStart = "context";
    private String msg;
    private String file;
    private String context;
    private static final String splitOperator = ":";
    private static final String tokenOperator = ",";
    private String author = "user";

    public IntegrityLcChangeSet() {}

    public IntegrityLcChangeSet(String line)
    {
        String[] tokens = line.split(tokenOperator);
        for (String token: tokens) {
            String[] split = token.trim().split(splitOperator);
            if (split[0].startsWith(messageStart)) {
                this.msg = split[1].trim();
            } else if (split[0].startsWith(fileStart)) {
                this.file = split[1].trim();
            } else if (split[0].startsWith(contextStart)) {
                this.context = split[1].trim();
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

    public void setMsg(String msg)
    {
    if (msg.endsWith(",")) 
    {
        msg = msg.substring(0, msg.length() - 1);
    }
    this.msg = msg;
    }
    
    public String getContext()
    {
	return context;
    }

    public void setContext(String context)
    {
	this.context = context;
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

    public void setFile(String file)
    {
	this.file  = file;
    }

    @Override
    public Collection<String> getAffectedPaths()
    {
	return Collections.emptyList();
    }

    @Override
    protected void setParent(@SuppressWarnings("rawtypes") ChangeLogSet parent)
    {
        super.setParent(parent);
    }
}
