package hudson.scm;

import hudson.model.AbstractBuild;
import hudson.model.User;
import hudson.scm.IntegrityChangeLogSet.IntegrityChangeLog;

import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Collection; 
import java.util.List; 

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * This class is a representation of all the Changes that were performed between builds
 * At this point we're essentially skipping the Change Set part and only working with the
 * entries within the Change Set, i.e. Change Logs.  I suspect at some future time when
 * we've got better API access to the actual Change Packages that went into a particular
 * build, then we can revamp this whole Change Log Set reporting capabilities
 */
public class IntegrityChangeLogSet extends ChangeLogSet<IntegrityChangeLog> 
{
	private List<IntegrityChangeLog> logs;
	private final String url;
	private String version;
	private String date;
	private String author;
	private String msg;
	
	/**
	 * IntegrityChangeLogSet is a collection of all the changes for this build
	 * @param build
	 */
	public IntegrityChangeLogSet(AbstractBuild<?, ?> build, List<IntegrityChangeLog> logs, String integrityURL) 
	{
		super(build);
        this.logs = Collections.unmodifiableList(logs);
        this.url = integrityURL;
        this.version = String.valueOf(build.getNumber());
        this.author = "user";
        this.date = IntegritySCM.SDF.format(new Date());
        this.msg = "MKS Integrity Change Log";
        for (IntegrityChangeLog log : logs)
        {
        	log.setParent(this);
        }
	}
	
    /**
     * Returns the list of Change Logs
     */
    public List<IntegrityChangeLog> getLogs() 
    {
        return logs;
    }
    
    /**
     * Returns the type of this Change Log Set
     */
    @Override
    public String getKind() 
    {
        return "integrity";
    }
    
    /**
     * Provides the Integrity URL for this Change Log Set
     * @return
     */
    public String getIntegrityURL()
    {
    	return url;
    }
    
	/**
	 * The Entry class defines the metadata related to an individual file change
	 */
	@ExportedBean(defaultVisibility=999)
	public static class IntegrityChangeLog extends ChangeLogSet.Entry  
	{ 
		private Collection<String> affectedPaths; 
		private String action;
		private String file;
		private String author;
		private String revision;
		private String date;
		private String annotation;
		private String differences;
		private String msg; 
         
		/**
		 * Default constructor for the Digester
		 */
		public IntegrityChangeLog()
		{	
		}
		
		/**
		 * IntegrityChangeLog Class Constructor
		 * @param parent
		 * @param affectedPaths
		 * @param author
		 * @param msg
		 */
		public IntegrityChangeLog(ChangeLogSet<IntegrityChangeLog> parent, Collection<String> affectedPaths, String author, String msg)  
		{ 
			super(); 
			setParent(parent); 
			this.affectedPaths = affectedPaths; 
			this.author = author; 
			this.msg = msg; 
		} 
		
        /**
         * Gets the IntegrityChangeLogSet to which this change set belongs.
         */
        public IntegrityChangeLogSet getParent() 
        {
            return (IntegrityChangeLogSet)super.getParent();
        }
	    
	    /**
	     * Because of the class loader difference, we need to extend this method
	     * to make it accessible to the rest of IntegritySCM 
	     */
	    @Override
	    protected void setParent(@SuppressWarnings("rawtypes") ChangeLogSet changeLogSet) 
	    {
	        super.setParent(changeLogSet);
	    }
	    
		/**
		 * Returns a collection of all affected paths
		 */
		public Collection<String> getAffectedPaths()
		{ 
			return affectedPaths; 
		} 

		/**
		 * Returns the author responsible for the change
		 * Note: This user must be defined in Hudson!
		 */
		@Exported
		public User getAuthor()
		{ 
			if( author == null )
			{
				return User.getUnknown();
			}
			return User.get(author);
		} 

		/**
		 * Gets the user responsible for the change
		 */
		@Exported
		public String getUser()
		{
			return author;
		}
		
		/**
		 * Sets the user responsible for the change
		 * @param user
		 */
		public void setUser(String user)
		{
			this.author = user;
		}
				
		/**
		 * Returns the comments associated with the change
		 */
		@Exported
		public String getMsg()
		{ 
			return msg; 
		} 

		/**
		 * Setter method to initialize the comments for this change
		 * @param msg
		 */
		public void setMsg(String msg)
		{
			this.msg = msg;
		}
		
		/**
		 * Returns the revision number associated with the change
		 */
		@Exported
		public String getRevision()
		{ 
			return revision; 
		} 

		/**
		 * Provides the mechanism to populate the revision string for this Entry
		 * @param revision
		 */
		public void setRevision(String revision) 
		{ 
			this.revision = revision; 
		} 

		/**
		 * Returns the modification timestamp for this Entry
		 * @param revision
		 */
		@Exported
		public String getDate()
		{ 
			return date; 
		} 

		/**
		 * Sets the date stamp for when this change was made
		 * @param date
		 */
		public void setDate(String date)
		{ 
			 this.date = date; 
		} 
	
		/**
		 * Returns the action associated with this change, i.e. add, update, or delete
		 * @return
		 */
		@Exported
		public String getAction()
		{
			return action;
		}
		
		/**
		 * Sets the action associated with this change, i.e. add, update, or delete
		 * @param action
		 */
		public void setAction(String action)
		{
			this.action = action;
		}
		
		/**
		 * Used by the stapler class to display an appropriate icon associated with the change
		 * @return
		 */
		@Exported
		public EditType getEditType() 
		{
		    if( action.equalsIgnoreCase("delete") ) 
		    {
		        return EditType.DELETE;
		    }
		    else if( action.equalsIgnoreCase("add") ) 
		    {
		        return EditType.ADD;
		    }
		    else
		    {
		    	return EditType.EDIT;
		    }
		}
		
		/**
		 * Returns the MKS Integrity Project Member path for this change
		 * @return
		 */
		public String getFile()
		{
			return file;
		}
		
		/**
		 * Sets the MKS Integrity Project Member path for this change
		 * @param file
		 */
		public void setFile(String file)
		{
			this.file = file;
		}
		
		/**
		 * Returns a string url representation containing the link to 
		 * the MKS Integrity Annotated Member view
		 * @return
		 */
		public String getAnnotation()
		{
			return annotation;
		}
		
		/**
		 * Sets a string url representation containing the link to 
		 * the MKS Integrity Annotated Member view
		 * @param annotation
		 */
		public void setAnnotation(String annotation)
		{
			this.annotation = annotation;
		}
		
		/**
		 * Returns a string representation containing the link to
		 * the MKS Integrity Member differences view
		 * @return
		 */
		public String getDifferences()
		{
			return differences;
		}
		
		/**
		 * Sets a string url representation containing the link to
		 * the MKS Integrity Member differences view
		 * @param differences
		 */
		public void setDifferences(String differences)
		{
			this.differences = differences;
		}
	} 
	
	/**
	 * Overridden Iterator implementation for the Integrity Change Logs in this Integrity Change Log Set
	 * @see java.lang.Iterable#iterator()
	 */
	public Iterator<IntegrityChangeLog> iterator() 
	{
		return logs.iterator();
	}

	/**
	 * Overridden isEmptySet() implementation for the Change Log Set 
	 * @see hudson.scm.ChangeLogSet#isEmptySet()
	 */
	@Override
	public boolean isEmptySet() 
	{
		return logs.isEmpty();
	}
	
	/** 
	 * Adds an entry to the change set. 
	 */ 
	public void addEntry(Collection<String> affectedPaths, String user, String msg) 
	{ 
		logs.add(addNewEntry(affectedPaths, user, msg)); 
	} 

	/** 
	 * Returns a new IntegrityChangeLog, which is already added to the list. 
	 * @return new IntegrityChangeLog instance 
	 */ 
	public IntegrityChangeLog addNewEntry(Collection<String> affectedPaths, String user, String msg) 
	{ 
		IntegrityChangeLog log = new IntegrityChangeLog(this, affectedPaths, user, msg); 
		logs.add(log); 
		return log; 
	} 
	
	/**
	 * Returns the version information associated with this Change Set
	 * @return
	 */
	public String getVersion()
	{
		return version;
	}
	
	/**
	 * Sets the version information associated with this Change Set
	 * @param version
	 */
	public void setVersion(String version)
	{
		this.version = version;
	}
	
	/**
	 * Returns the date/time information of when this Change Set was created
	 * @return
	 */
	public String getDate()
	{
		return date;
	}
	
	/**
	 * Sets the date/time information of when this Change Set was created
	 * @param date
	 */
	public void setDate(String date)
	{
		this.date = date;
	}
	
	/**
	 * Returns the author responsible for creating this Change Set
	 * @return
	 */
	public String getAuthor()
	{
		return author;
	}
	
	/**
	 * Sets the author responsible for creating this Change Set
	 * @param author
	 */
	public void setAuthor(String author)
	{
		this.author = author;
	}
	
	/**
	 * Returns the comments associated with this Change Set
	 * @return
	 */
	public String getMsg()
	{
		return msg;
	}
	
	/**
	 * Sets the comments associated this Change Set
	 * @param msg
	 */
	public void setMsg(String msg)
	{
		this.msg = msg;
	}
}
