package hudson.scm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sql.ConnectionPoolDataSource;

import org.apache.commons.io.IOUtils;
import org.apache.derby.jdbc.EmbeddedConnectionPoolDataSource;

import com.mks.api.response.APIException;
import com.mks.api.response.Field;
import com.mks.api.response.WorkItem;
import com.mks.api.response.WorkItemIterator;
import com.mks.api.si.SIModelTypeName;

import hudson.scm.IntegritySCM.DescriptorImpl;

/**
 * This class provides certain utility functions for working with the embedded derby database
 */
public class DerbyUtils 
{
	private static final Logger LOGGER = Logger.getLogger(IntegritySCM.class.getSimpleName());
	public static final String DERBY_DRIVER = "org.apache.derby.jdbc.EmbeddedDriver";
	public static final String DERBY_SYS_HOME_PROPERTY = "derby.system.home";
	public static final String DERBY_URL_PREFIX = "jdbc:derby:";
	private static final String DERBY_DB_NAME = IntegritySCM.class.getSimpleName();
	public static final String CREATE_INTEGRITY_SCM_REGISTRY = "CREATE TABLE INTEGRITY_SCM_REGISTRY (" +
																	"ID INTEGER NOT NULL PRIMARY KEY GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1), " +
																	"JOB_NAME VARCHAR(256) NOT NULL, " +
																	"CONFIGURATION_NAME VARCHAR(50) NOT NULL, " +
																	"PROJECT_CACHE_TABLE VARCHAR(50) NOT NULL, " +
																	"BUILD_NUMBER BIGINT NOT NULL)";
	public static final String SELECT_REGISTRY_1 = "SELECT ID FROM INTEGRITY_SCM_REGISTRY WHERE ID = 1";	
	public static final String SELECT_REGISTRY_TABLE = "SELECT PROJECT_CACHE_TABLE FROM INTEGRITY_SCM_REGISTRY WHERE JOB_NAME = ? AND CONFIGURATION_NAME = ? AND BUILD_NUMBER = ?";
	public static final String SELECT_REGISTRY_TABLE_DROP = "SELECT PROJECT_CACHE_TABLE FROM INTEGRITY_SCM_REGISTRY WHERE JOB_NAME = ? AND BUILD_NUMBER = ?";
	public static final String INSERT_REGISTRY_ENTRY = "INSERT INTO INTEGRITY_SCM_REGISTRY (JOB_NAME, CONFIGURATION_NAME, PROJECT_CACHE_TABLE, BUILD_NUMBER) " + "VALUES (?, ?, ?, ?)";
	public static final String SELECT_REGISTRY_DISTINCT_PROJECTS = "SELECT DISTINCT JOB_NAME FROM INTEGRITY_SCM_REGISTRY";
	public static final String SELECT_REGISTRY_PROJECTS = "SELECT PROJECT_CACHE_TABLE FROM INTEGRITY_SCM_REGISTRY WHERE JOB_NAME = ? AND CONFIGURATION_NAME = ? ORDER BY BUILD_NUMBER DESC";
	public static final String SELECT_REGISTRY_PROJECT = "SELECT PROJECT_CACHE_TABLE FROM INTEGRITY_SCM_REGISTRY WHERE JOB_NAME = ?";
	public static final String DROP_REGISTRY_ENTRY = "DELETE FROM INTEGRITY_SCM_REGISTRY WHERE PROJECT_CACHE_TABLE = ?"; 
	public static final String CREATE_PROJECT_TABLE = "CREATE TABLE CM_PROJECT (" +
														CM_PROJECT.ID + " INTEGER NOT NULL " + 
														"PRIMARY KEY GENERATED ALWAYS AS IDENTITY " + 
														"(START WITH 1, INCREMENT BY 1), " +
														CM_PROJECT.TYPE + " SMALLINT NOT NULL, " +		/* 0 = File; 1 = Directory */
														CM_PROJECT.NAME + " VARCHAR(32500) NOT NULL, " +
														CM_PROJECT.MEMBER_ID + " VARCHAR(32500), " +														
														CM_PROJECT.TIMESTAMP + " TIMESTAMP, " +
														CM_PROJECT.DESCRIPTION + " CLOB(4 M), " +
														CM_PROJECT.AUTHOR + " VARCHAR(100), " +
														CM_PROJECT.CONFIG_PATH + " VARCHAR(32500), " +
														CM_PROJECT.REVISION + " VARCHAR(32500), " +
														CM_PROJECT.OLD_REVISION + " VARCHAR(32500), " +														
														CM_PROJECT.RELATIVE_FILE + " VARCHAR(32500), " +
														CM_PROJECT.CHECKSUM + " VARCHAR(32), " +
														CM_PROJECT.DELTA + " SMALLINT, " +
														CM_PROJECT.CPID + " VARCHAR(32500) ) "; 		/* 0 = Unchanged; 1 = Added; 2 = Changed; 3 = Dropped */
	public static final String DROP_PROJECT_TABLE = "DROP TABLE CM_PROJECT";
	public static final String SELECT_MEMBER_1 = "SELECT " + CM_PROJECT.ID + " FROM CM_PROJECT WHERE " + CM_PROJECT.ID + " = 1";	
	public static final String INSERT_MEMBER_RECORD = "INSERT INTO CM_PROJECT " +
														"(" + CM_PROJECT.TYPE + ", " + CM_PROJECT.NAME + ", " + CM_PROJECT.MEMBER_ID + ", " +
														CM_PROJECT.TIMESTAMP + ", " + CM_PROJECT.DESCRIPTION + ", " + CM_PROJECT.CONFIG_PATH + ", " +
														CM_PROJECT.REVISION + ", " + CM_PROJECT.RELATIVE_FILE + ", " + CM_PROJECT.CPID + ") " + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
	public static final String BASELINE_SELECT = "SELECT " + CM_PROJECT.NAME + ", " + CM_PROJECT.MEMBER_ID + ", " + CM_PROJECT.TIMESTAMP + ", " +
													CM_PROJECT.DESCRIPTION + ", " + CM_PROJECT.AUTHOR + ", " + CM_PROJECT.CONFIG_PATH + ", " +
													CM_PROJECT.REVISION + ", " + CM_PROJECT.RELATIVE_FILE + ", " + CM_PROJECT.CHECKSUM +
													" FROM CM_PROJECT WHERE " + CM_PROJECT.TYPE + " = 0 AND (" + 
													CM_PROJECT.DELTA + " IS NULL OR " + CM_PROJECT.DELTA + " <> 3)";
	public static final String DELTA_SELECT = "SELECT " + CM_PROJECT.TYPE + ", " + CM_PROJECT.NAME + ", " + CM_PROJECT.MEMBER_ID + ", " +
												CM_PROJECT.TIMESTAMP + ", " + CM_PROJECT.DESCRIPTION + ", " + CM_PROJECT.AUTHOR + ", " +
												CM_PROJECT.CONFIG_PATH + ", " + CM_PROJECT.REVISION + ", " + CM_PROJECT.OLD_REVISION + ", " +
												CM_PROJECT.RELATIVE_FILE + ", " + CM_PROJECT.CHECKSUM + ", " + CM_PROJECT.DELTA + ", " + CM_PROJECT.CPID +
												" FROM CM_PROJECT WHERE " + CM_PROJECT.TYPE + " = 0";
	public static final String PROJECT_SELECT = "SELECT " + CM_PROJECT.NAME + ", " + CM_PROJECT.MEMBER_ID + ", " + CM_PROJECT.TIMESTAMP + ", " +
												CM_PROJECT.DESCRIPTION + ", " + CM_PROJECT.AUTHOR + ", " + CM_PROJECT.CONFIG_PATH + ", " +
												CM_PROJECT.REVISION + ", " + CM_PROJECT.OLD_REVISION + ", " + CM_PROJECT.RELATIVE_FILE + ", " + 
												CM_PROJECT.CHECKSUM + ", " + CM_PROJECT.DELTA + ", " + CM_PROJECT.CPID +
												" FROM CM_PROJECT WHERE " + CM_PROJECT.TYPE + " = 0 ORDER BY " + CM_PROJECT.NAME + " ASC";

	public static final String SUB_PROJECT_SELECT = "SELECT " + CM_PROJECT.NAME + ", " + CM_PROJECT.CONFIG_PATH + ", " +  CM_PROJECT.REVISION +
												" FROM CM_PROJECT WHERE " + CM_PROJECT.TYPE + " = 1 ORDER BY " + CM_PROJECT.CONFIG_PATH + " ASC";	
	
	public static final String AUTHOR_SELECT = "SELECT " + CM_PROJECT.NAME + ", " + CM_PROJECT.MEMBER_ID + ", " + CM_PROJECT.AUTHOR + ", " +
												CM_PROJECT.CONFIG_PATH + ", " + CM_PROJECT.REVISION + " FROM CM_PROJECT WHERE " + 
												CM_PROJECT.TYPE + " = 0 AND (" + CM_PROJECT.DELTA + " IS NULL OR " + CM_PROJECT.DELTA + " <> 3)";
	public static final String DIR_SELECT = "SELECT DISTINCT " + CM_PROJECT.RELATIVE_FILE + " FROM CM_PROJECT WHERE " + 
												CM_PROJECT.TYPE + " = 1 ORDER BY " + CM_PROJECT.RELATIVE_FILE + " ASC";
	public static final String CHECKSUM_UPDATE = "SELECT " + CM_PROJECT.NAME + ", " + CM_PROJECT.CHECKSUM + " FROM CM_PROJECT WHERE " + 
													CM_PROJECT.TYPE + " = 0 AND (" + CM_PROJECT.DELTA + " IS NULL OR " + CM_PROJECT.DELTA + " <> 3)";	
	
	/**
	 * Returns the CM_PROJECT column name for the string column name
	 * @param name
	 * @return
	 */
	public static final CM_PROJECT getEnum(String name)
	{
		CM_PROJECT[] values = CM_PROJECT.values();
		for( int i = 0; i < values.length; i++ )
		{
			if( name.equals(values[i].toString()) )
			{
				return values[i];
			}
		}
		return CM_PROJECT.UNDEFINED;
	}

	/**
	 * Random unique id generator for cache table names
	 * @return
	 */
	public static final String getUUIDTableName()
	{
		return "SCM_" + UUID.randomUUID().toString().replace('-', '_');
	}
	
	/**
	 * Utility function to load the Java DB Driver
	 */
	public static void loadDerbyDriver() 
	{
	    try 
	    {
	    	LOGGER.fine("Loading derby driver: " + DERBY_DRIVER);
	        Class.forName(DERBY_DRIVER);
	    }
	    catch( ClassNotFoundException ex ) 
	    {
	    	LOGGER.severe("Failed to load derby driver: " + DERBY_DRIVER);
	    	LOGGER.severe(ex.getMessage());
	    	LOGGER.log(Level.SEVERE, "ClassNotFoundException", ex);
	    }
	}

	/**
	 * Creates a pooled connection data source for the derby database
	 * @return
	 */
	public static ConnectionPoolDataSource createConnectionPoolDataSource(String derbyHome)
	{
		EmbeddedConnectionPoolDataSource dataSource = new EmbeddedConnectionPoolDataSource();
		dataSource.setCreateDatabase("create");
		dataSource.setDataSourceName(DERBY_URL_PREFIX + derbyHome.replace('\\',  '/') + "/" + DERBY_DB_NAME);
		dataSource.setDatabaseName(derbyHome.replace('\\',  '/') + "/" + DERBY_DB_NAME);

		return dataSource;
		
	}

	/**
	 * Generic SQL statement execution function
	 * @param dataSource A pooled connection data source
	 * @param sql String sql statement
	 * @return
	 * @throws SQLException
	 */
	public static synchronized boolean executeStmt(ConnectionPoolDataSource dataSource, String sql) throws SQLException
	{
		boolean success = false;
		Connection db = null;
		Statement stmt = null;
		try
		{
			LOGGER.fine("Preparing to execute " + sql);
			db = dataSource.getPooledConnection().getConnection();
			stmt = db.createStatement();
			success = stmt.execute(sql);
			LOGGER.fine("Executed...!");
		}
		catch(SQLException sqlex)
		{
			throw sqlex;
		}
		finally
		{
			if( null != stmt )
			{
				stmt.close();
			}
			
			if( null != db )
			{
				db.close();
			}
		}
		
		return success;
	}

	/**
	 * Creates the Integrity SCM cache registry table
	 * @param dataSource
	 * @return
	 */
	public static synchronized boolean createRegistry(ConnectionPoolDataSource dataSource)
	{
		boolean tableCreated = false;
		try
		{
			if( executeStmt(dataSource, SELECT_REGISTRY_1) )
			{
				LOGGER.fine("Integrity SCM cache registry table exists...");
				tableCreated = true;
			}
		} 
		catch( SQLException ex ) 
		{
			LOGGER.fine(ex.getMessage());
			try
			{
				LOGGER.fine("Integrity SCM cache registry doesn't exist, creating...");				
				tableCreated = executeStmt(dataSource, CREATE_INTEGRITY_SCM_REGISTRY);
			}
			catch( SQLException sqlex )
			{
				LOGGER.fine("Failed to create Integrity SCM cache registry table!");
				LOGGER.log(Level.SEVERE, "SQLException", sqlex);
				tableCreated = false;
			}
		}
		
		return tableCreated;
	}	
	
	/**
	 * Creates a single Integrity SCM Project/Configuration cache table
	 * @param dataSource
	 * @param jobName
	 * @param configurationName
	 * @param buildNumber
	 * @return
	 * @throws SQLException
	 */
	public static synchronized String registerProjectCache(ConnectionPoolDataSource dataSource, String jobName, String configurationName, long buildNumber) throws SQLException
	{
		String cacheTableName = "";
		Connection db = null;
		PreparedStatement select = null;
		PreparedStatement insert = null;
		ResultSet rs = null;
		
		try
		{
			// First Check to see if the current project registry exists
			db = dataSource.getPooledConnection().getConnection();
			cacheTableName = getProjectCache(dataSource, jobName, configurationName, buildNumber);
			if( null == cacheTableName || cacheTableName.length() == 0 )
			{
				// Insert a new row in the registry
				String uuid = getUUIDTableName();
				insert = db.prepareStatement(INSERT_REGISTRY_ENTRY);
				insert.clearParameters();
				insert.setString(1, jobName);			// JOB_NAME
				insert.setString(2, configurationName);	// CONFIGURATION_NAME
				insert.setString(3, uuid);				// PROJECT_CACHE_TABLE
				insert.setLong(4, buildNumber);			// BUILD_NUMBER
				insert.executeUpdate();
				cacheTableName = uuid;
			}
		}
		catch( SQLException sqlex )
		{
			LOGGER.fine(String.format("Failed to create Integrity SCM cache registry entry for %s/%s/%d!", jobName, configurationName, buildNumber));
			LOGGER.log(Level.SEVERE, "SQLException", sqlex);
		}
		finally
		{
			if( null != select ){ select.close(); }
			if( null != rs ){ rs.close(); }
			if( null != insert ){ insert.close(); }
			if( null != db ){ db.close(); }
		}	
		
		return cacheTableName;
	}
	
	/**
	 * Returns the name of the project cache table for the specified job/configuration and build
	 * @param dataSource
	 * @param jobName
	 * @param configurationName
	 * @param buildNumber
	 * @return
	 * @throws SQLException
	 */
	public static synchronized String getProjectCache(ConnectionPoolDataSource dataSource, String jobName, String configurationName, long buildNumber) throws SQLException
	{
		String cacheTableName = "";
		Connection db = null;
		PreparedStatement select = null;
		PreparedStatement insert = null;
		ResultSet rs = null;
		
		try
		{
			db = dataSource.getPooledConnection().getConnection();
			select = db.prepareStatement(SELECT_REGISTRY_TABLE, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
			select.setString(1, jobName);
			select.setString(2, configurationName);
			select.setLong(3, buildNumber);
			rs = select.executeQuery();
			if( getRowCount(rs) > 0 )
			{
				rs.next();
				cacheTableName = rs.getString("PROJECT_CACHE_TABLE");	
			}
		}
		catch( SQLException sqlex )
		{
			LOGGER.fine(String.format("Failed to get Integrity SCM cache registry entry for %s/%s/%d!", jobName, configurationName, buildNumber));
			LOGGER.log(Level.SEVERE, "SQLException", sqlex);	
		}
		finally
		{
			if( null != select ){ select.close(); }
			if( null != rs ){ rs.close(); }
			if( null != insert ){ insert.close(); }
			if( null != db ){ db.close(); }
		}	
		
		return cacheTableName;
	}	
	
	/**
	 * Maintenance function that returns a list of distinct job names 
	 * for additional checking to see which ones are inactive
	 * @param dataSource
	 * @return
	 * @throws SQLException
	 */
	public static synchronized List<String> getDistinctJobNames(ConnectionPoolDataSource dataSource) throws SQLException
	{
		List<String> jobsList = new ArrayList<String>();
		Connection db = null;
		PreparedStatement select = null;
		PreparedStatement delete = null;
		ResultSet rs = null;
		
		try
		{
			// Get a connection from the pool
			db = dataSource.getPooledConnection().getConnection();
			// First Check to see if the current project registry exists
			LOGGER.fine("Preparing to execute " + SELECT_REGISTRY_DISTINCT_PROJECTS);
			select = db.prepareStatement(SELECT_REGISTRY_DISTINCT_PROJECTS);
			rs = select.executeQuery();
			LOGGER.fine("Executed!");
			while( rs.next() )
			{
				String job = rs.getString("JOB_NAME");
				jobsList.add(job);
				LOGGER.fine(String.format("Adding job '%s' from the list of registered projects cache",  job));
			}
		}
		catch( SQLException sqlex )
		{
			LOGGER.fine("Failed to run distinct jobs query!");
			LOGGER.log(Level.SEVERE, "SQLException", sqlex);
		}
		finally
		{
			if( null != select ){ select.close(); }
			if( null != rs ){ rs.close(); }
			if( null != delete ){ delete.close(); }
			if( null != db ){ db.close(); }
		}
		
		return jobsList;
	}
	
	/**
	 * Maintenance function to delete all inactive project cache tables
	 * @param dataSource
	 * @param jobName
	 * @throws SQLException
	 */
	public static synchronized void deleteProjectCache(ConnectionPoolDataSource dataSource, String jobName) throws SQLException
	{
		Connection db = null;
		PreparedStatement select = null;
		PreparedStatement delete = null;
		ResultSet rs = null;
		
		try
		{
			// Get a connection from the pool
			db = dataSource.getPooledConnection().getConnection();
			// First Check to see if the current project registry exists
			select = db.prepareStatement(SELECT_REGISTRY_PROJECT, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
			select.setString(1, jobName);
			delete = db.prepareStatement(DROP_REGISTRY_ENTRY);
			rs = select.executeQuery();
			if( getRowCount(rs) > 0 )
			{
				while( rs.next() )
				{
					String cacheTableName = rs.getString("PROJECT_CACHE_TABLE");	
					executeStmt(dataSource, DROP_PROJECT_TABLE.replaceFirst("CM_PROJECT", cacheTableName));
					delete.setString(1, cacheTableName);
					delete.addBatch();
				}
				
				delete.executeBatch();
			}
		}
		catch( SQLException sqlex )
		{
			LOGGER.fine("Failed to purge project '" + jobName + "' from Integrity SCM cache registry!");
			LOGGER.log(Level.SEVERE, "SQLException", sqlex);
		}
		finally
		{
			if( null != select ){ select.close(); }
			if( null != rs ){ rs.close(); }
			if( null != delete ){ delete.close(); }
			if( null != db ){ db.close(); }
		}
	}
	
	/**
	 * Maintenance function to limit project cache to the most recent builds
	 * @param dataSource
	 * @param jobName
	 * @param buildNumber
	 * @throws SQLException
	 */
	public static synchronized void cleanupProjectCache(ConnectionPoolDataSource dataSource, String jobName, long buildNumber) throws SQLException
	{
		Connection db = null;
		PreparedStatement select = null;
		PreparedStatement delete = null;
		ResultSet rs = null;
		
		try
		{
			// Get a connection from the pool			
			db = dataSource.getPooledConnection().getConnection();
			
			// First Check to see if the current project registry exists
			select = db.prepareStatement(SELECT_REGISTRY_TABLE_DROP, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
			select.setString(1, jobName);
			select.setLong(2, buildNumber);
			delete = db.prepareStatement(DROP_REGISTRY_ENTRY);
			rs = select.executeQuery();
			int rowCount = getRowCount(rs);
			int deleteCount = 0;
			LOGGER.fine("Cache entries for " + jobName + "/" + buildNumber + " = " + rowCount);
			while( rs.next() )
			{
				String cacheTableName = rs.getString("PROJECT_CACHE_TABLE");
				LOGGER.fine(String.format("Deleting old cache entry for %s/%s", jobName, cacheTableName));
				try
				{
					// Attempting to drop the old cache table						
					executeStmt(dataSource, DROP_PROJECT_TABLE.replaceFirst("CM_PROJECT", cacheTableName));
				}
				catch( SQLException sqlex )
				{
					// If this fails, then we'll have to investigate later...
					LOGGER.fine(String.format("Failed to drop table '%s' from Integrity SCM cache registry!", cacheTableName));
					LOGGER.log(Level.SEVERE, "SQLException", sqlex);
				}
					
				// Tag the cache entry for removal
				deleteCount++;
				delete.setString(1, cacheTableName);
				delete.addBatch();
			}
				
			// Remove the cache entry regardless of the whether or not the cache table was purged
			if( deleteCount > 0 )
			{
				delete.executeBatch();
			}
		}
		catch( SQLException sqlex )
		{
			LOGGER.fine(String.format("Failed to clear old cache for project '%s' from Integrity SCM cache registry!", jobName));
			LOGGER.log(Level.SEVERE, "SQLException", sqlex);
		}
		finally
		{
			if( null != select ){ select.close(); }
			if( null != rs ){ rs.close(); }
			if( null != delete ){ delete.close(); }
			if( null != db ){ db.close(); }
		}
	}
	
	/**
	 * Establishes a fresh set of Integrity SCM cache tables
	 * @param db Derby database connection
	 * @return true/false depending on the success of the operation
	 */
	public static synchronized boolean createCMProjectTables(ConnectionPoolDataSource dataSource, String tableName)
	{
		boolean tableCreated = false;
		try
		{
			if( executeStmt(dataSource, SELECT_MEMBER_1.replaceFirst("CM_PROJECT", tableName)) )
			{
				try
				{
					LOGGER.fine("A prior set of Integrity SCM cache tables detected, dropping...");
					tableCreated = executeStmt(dataSource, DROP_PROJECT_TABLE.replaceFirst("CM_PROJECT", tableName));
					LOGGER.fine("Recreating a fresh set of Integrity SCM cache tables...");
					tableCreated = executeStmt(dataSource, CREATE_PROJECT_TABLE.replaceFirst("CM_PROJECT", tableName));
				}
				catch( SQLException ex )
				{
					LOGGER.fine(String.format("Failed to create Integrity SCM project cache table '%s'", tableName));
					LOGGER.log(Level.SEVERE, "SQLException", ex);
					tableCreated = false;
				}
			}
		} 
		catch( SQLException ex ) 
		{
			LOGGER.fine(ex.getMessage());
			try
			{
				LOGGER.fine(String.format("Integrity SCM cache table '%s' does not exist, creating...", tableName));				
				tableCreated = executeStmt(dataSource, CREATE_PROJECT_TABLE.replaceFirst("CM_PROJECT", tableName));
			}
			catch( SQLException sqlex )
			{
				LOGGER.fine(String.format("Failed to create Integrity SCM project cache table '%s'", tableName));
				LOGGER.log(Level.SEVERE, "SQLException", sqlex);
				tableCreated = false;
			}
		}
		
		return tableCreated;
	}
	
	/**
	 * Convenience function that converts a result set row into a Hashtable for easy access
	 * @param rs ResultSet row object
	 * @return Hashtable containing the non-null values for each column
	 * @throws SQLException
	 * @throws IOException
	 */
	public static Hashtable<CM_PROJECT, Object> getRowData(ResultSet rs) throws SQLException, IOException
	{
		Hashtable<CM_PROJECT, Object> rowData = new Hashtable<CM_PROJECT, Object>();
		ResultSetMetaData rsMetaData = rs.getMetaData();
		int columns = rsMetaData.getColumnCount();
		for( int i = 1; i <= columns; i++ )
		{
			int columnType = rsMetaData.getColumnType(i);
			@SuppressWarnings("unused")
			Object value = null;
			switch(columnType)
			{
				case java.sql.Types.ARRAY:
					value = rs.getArray(i);
					if( !rs.wasNull() ){ rowData.put(getEnum(rsMetaData.getColumnLabel(i)), rs.getArray(i)); }
					break;
					
				case java.sql.Types.BIGINT:
				case java.sql.Types.NUMERIC:
				case java.sql.Types.REAL:					
					value = rs.getLong(i);
					if( !rs.wasNull() ){ rowData.put(getEnum(rsMetaData.getColumnLabel(i)), rs.getLong(i)); }					
					break;
					
				case java.sql.Types.BLOB:
					InputStream is = null;
					try
					{
						is = rs.getBlob(i).getBinaryStream();
						byte[] bytes = IOUtils.toByteArray(is);
						rowData.put(getEnum(rsMetaData.getColumnLabel(i)), bytes);
					}
					finally
					{
						if( null != is ){ is.close(); }
					}
					break;
					
				case java.sql.Types.BOOLEAN:
					value = rs.getBoolean(i);
					if( !rs.wasNull() ){ rowData.put(getEnum(rsMetaData.getColumnLabel(i)), rs.getBoolean(i)); }
					break;
					
				case java.sql.Types.CLOB:
					BufferedReader reader = null;
					try
					{
						reader = new java.io.BufferedReader(rs.getClob(i).getCharacterStream());
						String line = null;
						StringBuilder sb = new StringBuilder();
						while( null != (line=reader.readLine()) ){ sb.append(line + IntegritySCM.NL); }
						rowData.put(getEnum(rsMetaData.getColumnLabel(i)), sb.toString());
					}
					finally
					{
						if( null != reader ){ reader.close(); }
					}
					break;
					
				case java.sql.Types.DATE:
					value = rs.getDate(i);
					if( !rs.wasNull() ){ rowData.put(getEnum(rsMetaData.getColumnLabel(i)), rs.getDate(i)); }
					break;
					
				case java.sql.Types.DECIMAL:
					value = rs.getBigDecimal(i);
					if( !rs.wasNull() ){ rowData.put(getEnum(rsMetaData.getColumnLabel(i)), rs.getBigDecimal(i)); }
					break;
					
				case java.sql.Types.DOUBLE:
					value = rs.getDouble(i);
					if( !rs.wasNull() ){ rowData.put(getEnum(rsMetaData.getColumnLabel(i)), rs.getDouble(i)); }
					break;
					
				case java.sql.Types.FLOAT:
					value = rs.getFloat(i);
					if( !rs.wasNull() ){ rowData.put(getEnum(rsMetaData.getColumnLabel(i)), rs.getFloat(i)); }
					break;
					
				case java.sql.Types.INTEGER:
					value = rs.getInt(i);
					if( !rs.wasNull() ){ rowData.put(getEnum(rsMetaData.getColumnLabel(i)), rs.getInt(i)); }
					break;
					
				case java.sql.Types.JAVA_OBJECT:
					try
					{
						rowData.put(getEnum(rsMetaData.getColumnLabel(i)), rs.getObject(i));
					}
					finally
					{
						
					}
					break;
					
				case java.sql.Types.SMALLINT:
				case java.sql.Types.TINYINT:
					value = rs.getShort(i);
					if( !rs.wasNull() ){ rowData.put(getEnum(rsMetaData.getColumnLabel(i)), rs.getShort(i)); }
					break;

				case java.sql.Types.TIME:
					value = rs.getTime(i);
					if( !rs.wasNull() ){ rowData.put(getEnum(rsMetaData.getColumnLabel(i)), rs.getTime(i)); }
					break;
					
				case java.sql.Types.TIMESTAMP:					
					value = rs.getTimestamp(i);
					if( !rs.wasNull() ){ rowData.put(getEnum(rsMetaData.getColumnLabel(i)), rs.getTimestamp(i)); }
					break;
										
				default:
					value = rs.getString(i);
					if( !rs.wasNull() ){ rowData.put(getEnum(rsMetaData.getColumnLabel(i)), rs.getString(i)); }
			}

		}

		return rowData;
	}

	/**
	 * This function provides a count of the total number of rows in the ResultSet
	 * @param set
	 * @return
	 * @throws SQLException
	 */
	public static int getRowCount(ResultSet rs) throws SQLException   
	{   
		int rowCount = 0;   
		int currentRow = rs.getRow();   
		rowCount = rs.last() ? rs.getRow() : rowCount;   
		if( currentRow == 0 )
		{
			rs.beforeFirst();
		}
		else
		{   
			rs.absolute(currentRow);
		}
		
		return rowCount;   
	}	
	
	/**
	 * Attempts to fix known issues with characters that can potentially break the change log xml
	 * @param desc Input comment string for the revision
	 * @return Sanitized string that can be embedded within a CDATA tag
	 */
	public static String fixDescription(String desc)
	{
		// Char 8211 which is a long dash causes problems for the change log XML, need to fix it!
		String description = desc.replace((char)8211, '-');
		return description.replaceAll("<!\\[CDATA\\[", "< ! [ CDATA [").replaceAll("\\]\\]>", "] ] >");
	}
	
	/**
	 * Compares this version of the project to a previous/new version to determine what are the updates and what was deleted
	 * @param baselineProjectCache The previous baseline (build) for this Integrity CM Project
	 * @param return The total number of changes found in the comparison
	 * @throws SQLException 
	 * @throws IOException 
	 */
	public static synchronized int compareBaseline(String serverConfigId, String baselineProjectCache, String projectCacheTable, Map<String, String> membersInCP, boolean skipAuthorInfo, boolean CPMode) throws SQLException, IOException
	{
		// Re-initialize our return variable
		int changeCount = 0;
		
		Connection db = null;
		Statement baselineSelect = null;
		Statement pjSelect = null;
		ResultSet baselineRS = null;
		ResultSet rs = null;
		
		try
		{
			if(CPMode)
			{
				if(membersInCP.isEmpty())
					return changeCount;
			}
			// Get a connection from our pool
			db = DescriptorImpl.INTEGRITY_DESCRIPTOR.getDataSource().getPooledConnection().getConnection();				
			// Create the select statement for the previous baseline
			baselineSelect = db.createStatement();
			String baselineSelectSql = DerbyUtils.BASELINE_SELECT.replaceFirst("CM_PROJECT", baselineProjectCache);
			LOGGER.fine("Attempting to execute query " + baselineSelectSql);
			baselineRS = baselineSelect.executeQuery(baselineSelectSql);
		
			// Create a hashtable to hold the old baseline for easy comparison
			Hashtable<String, Hashtable<CM_PROJECT,Object>> baselinePJ = new Hashtable<String, Hashtable<CM_PROJECT,Object>>();
			while( baselineRS.next() )
			{
				Hashtable<CM_PROJECT, Object> rowHash = DerbyUtils.getRowData(baselineRS);
				Hashtable<CM_PROJECT, Object> memberInfo = new Hashtable<CM_PROJECT, Object>();
				memberInfo.put(CM_PROJECT.MEMBER_ID, (null == rowHash.get(CM_PROJECT.MEMBER_ID) ? "" : rowHash.get(CM_PROJECT.MEMBER_ID).toString()));
				memberInfo.put(CM_PROJECT.TIMESTAMP, (null == rowHash.get(CM_PROJECT.TIMESTAMP) ? "" : (Date)rowHash.get(CM_PROJECT.TIMESTAMP)));
				memberInfo.put(CM_PROJECT.DESCRIPTION, (null == rowHash.get(CM_PROJECT.DESCRIPTION) ? "" : rowHash.get(CM_PROJECT.DESCRIPTION).toString()));
				memberInfo.put(CM_PROJECT.AUTHOR, (null == rowHash.get(CM_PROJECT.AUTHOR) ? "" : rowHash.get(CM_PROJECT.AUTHOR).toString()));
				memberInfo.put(CM_PROJECT.CONFIG_PATH, (null == rowHash.get(CM_PROJECT.CONFIG_PATH) ? "" : rowHash.get(CM_PROJECT.CONFIG_PATH).toString()));
				memberInfo.put(CM_PROJECT.REVISION, (null == rowHash.get(CM_PROJECT.REVISION) ? "" : rowHash.get(CM_PROJECT.REVISION).toString()));
				memberInfo.put(CM_PROJECT.CPID, (null == rowHash.get(CM_PROJECT.CPID) ? "" : rowHash.get(CM_PROJECT.CPID).toString()));
				memberInfo.put(CM_PROJECT.RELATIVE_FILE, (null == rowHash.get(CM_PROJECT.RELATIVE_FILE) ? "" : rowHash.get(CM_PROJECT.RELATIVE_FILE).toString()));
				memberInfo.put(CM_PROJECT.CHECKSUM, (null == rowHash.get(CM_PROJECT.CHECKSUM) ? "" : rowHash.get(CM_PROJECT.CHECKSUM).toString()));
				baselinePJ.put(rowHash.get(CM_PROJECT.NAME).toString(), memberInfo);
			}
			
			// Create the select statement for the current project
			pjSelect = db.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			String pjSelectSql = DerbyUtils.DELTA_SELECT.replaceFirst("CM_PROJECT", projectCacheTable);
			LOGGER.fine("Attempting to execute query " + pjSelectSql);
			rs = pjSelect.executeQuery(pjSelectSql);
			
			// Now we will compare the adds and updates between the current project and the baseline
			for( int i = 1; i <= DerbyUtils.getRowCount(rs); i++ )
			{
				// Move the cursor to the current record
				rs.absolute(i);
				Hashtable<CM_PROJECT, Object> rowHash = DerbyUtils.getRowData(rs);
				// Obtain the member we're working with
				String memberName = rowHash.get(CM_PROJECT.NAME).toString();				
				
				// Get the baseline project information for this member
				LOGGER.fine("Comparing file against baseline " + memberName);
				Hashtable<CM_PROJECT, Object> baselineMemberInfo = baselinePJ.get(memberName);
				
				if(CPMode)
				{
					if(!membersInCP.containsKey(memberName))
					{
						// This member did not change, so lets copy its old author information
						if( null != baselineMemberInfo.get(CM_PROJECT.AUTHOR) )
						{
							rs.updateString(CM_PROJECT.AUTHOR.toString(), baselineMemberInfo.get(CM_PROJECT.AUTHOR).toString());
						}
						// Also, lets copy over the previous MD5 checksum
						if( null != baselineMemberInfo.get(CM_PROJECT.CHECKSUM) )
						{
							rs.updateString(CM_PROJECT.CHECKSUM.toString(), baselineMemberInfo.get(CM_PROJECT.CHECKSUM).toString());
						}
						// Initialize the delta flag
						rs.updateShort(CM_PROJECT.DELTA.toString(), (short)0);
						
						// Remove this member from the baseline project hashtable, so we'll be left with items that are dropped
						baselinePJ.remove(memberName);
						
						// Update this row in the data source
						rs.updateRow();	
						
						continue;
					}else{
						// Initialize the cpid
						String cpid = membersInCP.get(memberName);
						rs.updateString(CM_PROJECT.CPID.toString(), cpid);						
						
						// Update this row in the data source
						rs.updateRow();	
					}
				}
				
				// This file was in the previous baseline as well...
				if( null != baselineMemberInfo )
				{
					// Did it change? Either by an update or roll back (update member revision)?
					String oldRevision = baselineMemberInfo.get(CM_PROJECT.REVISION).toString();
					if( ! rowHash.get(CM_PROJECT.REVISION).toString().equals(oldRevision) )
					{
						// Initialize the prior revision
						rs.updateString(CM_PROJECT.OLD_REVISION.toString(), oldRevision);
						// Initialize the author information as requested
						if( ! skipAuthorInfo ){ rs.updateString(CM_PROJECT.AUTHOR.toString(), 
													IntegrityCMMember.getAuthorFromRevisionInfo(serverConfigId, 
													rowHash.get(CM_PROJECT.CONFIG_PATH).toString(),
													rowHash.get(CM_PROJECT.MEMBER_ID).toString(),
													rowHash.get(CM_PROJECT.REVISION).toString())); }
						// Initialize the delta flag for this member
						rs.updateShort(CM_PROJECT.DELTA.toString(), (short)2);
						LOGGER.fine("... " + memberName + " revision changed - new revision is " + rowHash.get(CM_PROJECT.REVISION).toString());
						changeCount++;
					}
					else
					{
						// This member did not change, so lets copy its old author information
						if( null != baselineMemberInfo.get(CM_PROJECT.AUTHOR) )
						{
							rs.updateString(CM_PROJECT.AUTHOR.toString(), baselineMemberInfo.get(CM_PROJECT.AUTHOR).toString());
						}
						// Also, lets copy over the previous MD5 checksum
						if( null != baselineMemberInfo.get(CM_PROJECT.CHECKSUM) )
						{
							rs.updateString(CM_PROJECT.CHECKSUM.toString(), baselineMemberInfo.get(CM_PROJECT.CHECKSUM).toString());
						}
						// Initialize the delta flag
						rs.updateShort(CM_PROJECT.DELTA.toString(), (short)0);
					}
					
					// Remove this member from the baseline project hashtable, so we'll be left with items that are dropped
					baselinePJ.remove(memberName);
				}
				else // We've found a new file
				{
					// Initialize the author information as requested
					if( ! skipAuthorInfo ){ rs.updateString(CM_PROJECT.AUTHOR.toString(), 
												IntegrityCMMember.getAuthorFromRevisionInfo(serverConfigId, 
												rowHash.get(CM_PROJECT.CONFIG_PATH).toString(),
												rowHash.get(CM_PROJECT.MEMBER_ID).toString(),
												rowHash.get(CM_PROJECT.REVISION).toString())); }				
					// Initialize the delta flag for this member
					rs.updateShort(CM_PROJECT.DELTA.toString(), (short)1);
					LOGGER.fine("... " + memberName + " new file - revision is " + rowHash.get(CM_PROJECT.REVISION).toString());					
					changeCount++;
				}
				
				// Update this row in the data source
				rs.updateRow();				
			}
			
			// Now, we should be left with the drops.  Exist only in the old baseline and not the current one.
			Enumeration<String> deletedMembers = baselinePJ.keys();
			while( deletedMembers.hasMoreElements() )
			{
				changeCount++;
				String memberName = deletedMembers.nextElement();
				Hashtable<CM_PROJECT, Object> memberInfo = baselinePJ.get(memberName);
				
				// Add the deleted members to the database
				rs.moveToInsertRow();
				rs.updateShort(CM_PROJECT.TYPE.toString(), (short)0);
				rs.updateString(CM_PROJECT.NAME.toString(), memberName);
				rs.updateString(CM_PROJECT.MEMBER_ID.toString(), memberInfo.get(CM_PROJECT.MEMBER_ID).toString());
				if( memberInfo.get(CM_PROJECT.TIMESTAMP) instanceof java.util.Date )
				{
					Timestamp ts = new Timestamp(((Date)memberInfo.get(CM_PROJECT.TIMESTAMP)).getTime());
					rs.updateTimestamp(CM_PROJECT.TIMESTAMP.toString(), ts);
				}
				rs.updateString(CM_PROJECT.DESCRIPTION.toString(), memberInfo.get(CM_PROJECT.DESCRIPTION).toString());
				rs.updateString(CM_PROJECT.AUTHOR.toString(), memberInfo.get(CM_PROJECT.AUTHOR).toString());
				rs.updateString(CM_PROJECT.CONFIG_PATH.toString(), memberInfo.get(CM_PROJECT.CONFIG_PATH).toString());
				rs.updateString(CM_PROJECT.REVISION.toString(), memberInfo.get(CM_PROJECT.REVISION).toString());				
				rs.updateString(CM_PROJECT.RELATIVE_FILE.toString(), memberInfo.get(CM_PROJECT.RELATIVE_FILE).toString());
				rs.updateShort(CM_PROJECT.DELTA.toString(), (short)3);
				rs.updateString(CM_PROJECT.CPID.toString(), memberInfo.get(CM_PROJECT.CPID).toString());
				rs.insertRow();
				rs.moveToCurrentRow();
				
				LOGGER.fine("... " + memberName + " file dropped - revision was " + memberInfo.get(CM_PROJECT.REVISION).toString());
			}

			// Commit changes to the database...
			db.commit();
		}
		finally
		{
			// Close the result set and select statements
			if( null != baselineRS ){ baselineRS.close(); }
			if( null != rs ){ rs.close(); }			
			if( null != baselineSelect ){ baselineSelect.close(); }
			if( null != pjSelect ){ pjSelect.close(); }			
			
			// Close DB connection
			if( null != db ){ db.close(); }			
		}
		
		return changeCount;
	}	
	
	/**
	 * Updates the author information for all the members in the project
	 * @throws SQLException
	 * @throws IOException
	 */
	public static synchronized void primeAuthorInformation(String serverConfigId, String projectCacheTable) throws SQLException, IOException
	{
		Connection db = null;
		Statement authSelect = null;
		ResultSet rs = null;
		try
		{
			// Get a connection from our pool
			db = DescriptorImpl.INTEGRITY_DESCRIPTOR.getDataSource().getPooledConnection().getConnection();			
			// Create the select statement for the current project
			authSelect = db.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			rs = authSelect.executeQuery(DerbyUtils.AUTHOR_SELECT.replaceFirst("CM_PROJECT", projectCacheTable));
			while( rs.next() )
			{
				Hashtable<CM_PROJECT, Object> rowHash = DerbyUtils.getRowData(rs);
				rs.updateString(CM_PROJECT.AUTHOR.toString(), 
						IntegrityCMMember.getAuthorFromRevisionInfo(serverConfigId, rowHash.get(CM_PROJECT.CONFIG_PATH).toString(), 
											rowHash.get(CM_PROJECT.MEMBER_ID).toString(),
											rowHash.get(CM_PROJECT.REVISION).toString()));
				rs.updateRow();
			}
			
			// Commit the updates
			db.commit();
		}
		finally
		{
			// Release the result set
			if( null != rs ){ rs.close(); }
			
			// Release the statement
			if( null != authSelect ){ authSelect.close(); }
			
			// Close project db connections
			if( null != db ){ db.close(); }
		}
	}	
	
	/**
	 * Updates the underlying Integrity SCM Project table cache with the new checksum information
	 * @param checksumHash Checksum hashtable generated from a checkout operation
	 * @throws SQLException
	 * @throws IOException
	 */
	public static synchronized void updateChecksum(String projectCacheTable, ConcurrentHashMap<String, String> checksumHash) throws SQLException, IOException
	{
		Connection db = null;
		Statement checksumSelect = null;
		ResultSet rs = null;
		try
		{
			// Get a connection from our pool
			db = DescriptorImpl.INTEGRITY_DESCRIPTOR.getDataSource().getPooledConnection().getConnection();				
			// Create the select statement for the current project
			checksumSelect = db.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			rs = checksumSelect.executeQuery(DerbyUtils.CHECKSUM_UPDATE.replaceFirst("CM_PROJECT", projectCacheTable));
			while( rs.next() )
			{
				Hashtable<CM_PROJECT, Object> rowHash = DerbyUtils.getRowData(rs);
				String newChecksum = checksumHash.get(rowHash.get(CM_PROJECT.NAME).toString());
				if( null != newChecksum && newChecksum.length() > 0 )
				{
					rs.updateString(CM_PROJECT.CHECKSUM.toString(), newChecksum);
					rs.updateRow();
				}
			}
			
			// Commit the updates
			db.commit();
		}
		finally
		{
			// Release the result set
			if( null != rs ){ rs.close(); }
			
			// Release the statement
			if( null != checksumSelect ){ checksumSelect.close(); }
			
			// Close project db connections
			if( null != db ){ db.close(); }
		}
	}
	
	/**
	 * Project access function that returns the state of the current project
	 * NOTE: For maximum efficiency, this should be called only once and after the compareBasline() has been invoked!
	 * @return A List containing every member in this project, including any dropped artifacts
	 * @throws SQLException
	 * @throws IOException
	 */
	public static synchronized List<Hashtable<CM_PROJECT, Object>> viewProject(String projectCacheTable) throws SQLException, IOException
	{
		// Initialize our return variable
		List<Hashtable<CM_PROJECT, Object>> projectMembersList = new ArrayList<Hashtable<CM_PROJECT, Object>>();
		
		// Initialize our db connection
		Connection db = null;
		Statement stmt = null;
		ResultSet rs = null;
		
		try
		{
			// Get a connection from our pool
			db = DescriptorImpl.INTEGRITY_DESCRIPTOR.getDataSource().getPooledConnection().getConnection();				
			stmt = db.createStatement();
			rs = stmt.executeQuery(DerbyUtils.PROJECT_SELECT.replaceFirst("CM_PROJECT", projectCacheTable));
			while( rs.next() )
			{
				projectMembersList.add(DerbyUtils.getRowData(rs));
			}
		}
		finally
		{
			// Close the database resources
			if( null != rs ){ rs.close(); }
			if( null != stmt ){ stmt.close(); }
			if( null != db ){ db.close(); }
		}
		
		return projectMembersList;
	}	
	
	/**
	 * Project access function that returns the state of the current project
	 * NOTE: For maximum efficiency, this should be called only once and after the compareBasline() has been invoked!
	 * @return A List containing every subproject in this project
	 * @throws SQLException
	 * @throws IOException
	 */
	public static synchronized List<Hashtable<CM_PROJECT, Object>> viewSubProjects(String projectCacheTable) throws SQLException, IOException
	{
		// Initialize our return variable
		List<Hashtable<CM_PROJECT, Object>> subprojectsList = new ArrayList<Hashtable<CM_PROJECT, Object>>();
		
		// Initialize our db connection
		Connection db = null;
		Statement stmt = null;
		ResultSet rs = null;
		
		try
		{
			// Get a connection from our pool
			db = DescriptorImpl.INTEGRITY_DESCRIPTOR.getDataSource().getPooledConnection().getConnection();			
			stmt = db.createStatement();
			rs = stmt.executeQuery(DerbyUtils.SUB_PROJECT_SELECT.replaceFirst("CM_PROJECT", projectCacheTable));
			while( rs.next() )
			{
				subprojectsList.add(DerbyUtils.getRowData(rs));
			}
		}
		finally
		{
			// Close the database resources
			if( null != rs ){ rs.close(); }
			if( null != stmt ){ stmt.close(); }
			if( null != db ){ db.close(); }
		}
		
		return subprojectsList;
	}	
	
	/**
	 * Returns a string list of relative paths to all directories in this project
	 * @return
	 * @throws SQLException 
	 * @throws IOException 
	 */
	public static synchronized List<String> getDirList(String projectCacheTable) throws SQLException, IOException
	{
		// Initialize our return variable
		List<String> dirList = new ArrayList<String>();
		
		// Initialize our db connection
		Connection db = null;
		Statement stmt = null;
		ResultSet rs = null;
		
		try
		{
			// Get a connection from our pool
			db = DescriptorImpl.INTEGRITY_DESCRIPTOR.getDataSource().getPooledConnection().getConnection();			
			stmt = db.createStatement();
			rs = stmt.executeQuery(DerbyUtils.DIR_SELECT.replaceFirst("CM_PROJECT", projectCacheTable));
			while( rs.next() )
			{
				Hashtable<CM_PROJECT, Object> rowData = DerbyUtils.getRowData(rs); 
				dirList.add(rowData.get(CM_PROJECT.RELATIVE_FILE).toString());
			}
		}
		finally
		{
			// Close the database resources
			if( null != rs ){ rs.close(); }
			if( null != stmt ){ stmt.close(); }
			if( null != db ){ db.close(); }
		}
		
		return dirList;
	}	
}
