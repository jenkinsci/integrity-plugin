package hudson.scm;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Hashtable;

import org.apache.commons.io.IOUtils;

/**
 * This class provides certain utility functions for working with the embedded derby database
 */
public class DerbyUtils 
{
	public static final String DERBY_DRIVER = "org.apache.derby.jdbc.EmbeddedDriver";
	public static final String DERBY_SYS_HOME_PROPERTY = "derby.system.home";
	public static final String DERBY_DB_FOLDER = "IntegritySCM";
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
														CM_PROJECT.DELTA + " SMALLINT)"; 		/* 0 = Unchanged; 1 = Added; 2 = Changed; 3 = Dropped */
	public static final String DROP_PROJECT_TABLE = "DROP TABLE CM_PROJECT";
	public static final String CREATE_NAME_INDEX = "CREATE INDEX MEMBER_NAME ON CM_PROJECT (" + CM_PROJECT.NAME + " ASC)";
	public static final String DROP_NAME_INDEX = "DROP INDEX MEMBER_NAME";
	public static final String SELECT_MEMBER_1 = "SELECT " + CM_PROJECT.ID + " FROM CM_PROJECT WHERE " + CM_PROJECT.ID + " = 1";	
	public static final String INSERT_MEMBER_RECORD = "INSERT INTO CM_PROJECT " +
														"(" + CM_PROJECT.TYPE + ", " + CM_PROJECT.NAME + ", " + CM_PROJECT.MEMBER_ID + ", " +
														CM_PROJECT.TIMESTAMP + ", " + CM_PROJECT.DESCRIPTION + ", " + CM_PROJECT.CONFIG_PATH + ", " +
														CM_PROJECT.REVISION + ", " + CM_PROJECT.RELATIVE_FILE + ") " + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
	public static final String BASELINE_SELECT = "SELECT " + CM_PROJECT.NAME + ", " + CM_PROJECT.MEMBER_ID + ", " + CM_PROJECT.TIMESTAMP + ", " +
													CM_PROJECT.DESCRIPTION + ", " + CM_PROJECT.AUTHOR + ", " + CM_PROJECT.CONFIG_PATH + ", " +
													CM_PROJECT.REVISION + ", " + CM_PROJECT.RELATIVE_FILE + ", " + CM_PROJECT.CHECKSUM +
													" FROM CM_PROJECT WHERE " + CM_PROJECT.TYPE + " = 0 AND (" + 
													CM_PROJECT.DELTA + " IS NULL OR " + CM_PROJECT.DELTA + " <> 3)";
	public static final String DELTA_SELECT = "SELECT " + CM_PROJECT.TYPE + ", " + CM_PROJECT.NAME + ", " + CM_PROJECT.MEMBER_ID + ", " +
												CM_PROJECT.TIMESTAMP + ", " + CM_PROJECT.DESCRIPTION + ", " + CM_PROJECT.AUTHOR + ", " +
												CM_PROJECT.CONFIG_PATH + ", " + CM_PROJECT.REVISION + ", " + CM_PROJECT.OLD_REVISION + ", " +
												CM_PROJECT.RELATIVE_FILE + ", " + CM_PROJECT.CHECKSUM + ", " + CM_PROJECT.DELTA +
												" FROM CM_PROJECT WHERE " + CM_PROJECT.TYPE + " = 0";
	public static final String PROJECT_SELECT = "SELECT " + CM_PROJECT.NAME + ", " + CM_PROJECT.MEMBER_ID + ", " + CM_PROJECT.TIMESTAMP + ", " +
												CM_PROJECT.DESCRIPTION + ", " + CM_PROJECT.AUTHOR + ", " + CM_PROJECT.CONFIG_PATH + ", " +
												CM_PROJECT.REVISION + ", " + CM_PROJECT.OLD_REVISION + ", " + CM_PROJECT.RELATIVE_FILE + ", " + 
												CM_PROJECT.CHECKSUM + ", " + CM_PROJECT.DELTA +
												" FROM CM_PROJECT WHERE " + CM_PROJECT.TYPE + " = 0 ORDER BY " + CM_PROJECT.NAME + " ASC";	
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
	 * Utility function that sets the derby DB home
	 * @param dir
	 */
	public static void setDerbySystemDir(File dir)
	{
		System.setProperty(DERBY_SYS_HOME_PROPERTY, dir.getAbsolutePath());
	}

	/**
	 * Utility function to load the Java DB Driver
	 */
	public static void loadDerbyDriver() 
	{
	    try 
	    {
	    	Logger.debug("Loading derby driver: " + DERBY_DRIVER);
	        Class.forName(DERBY_DRIVER);
	    }
	    catch( ClassNotFoundException ex ) 
	    {
	    	Logger.error("Failed to load derby driver: " + DERBY_DRIVER);
	    	Logger.error(ex.getMessage());
	    	Logger.fatal(ex);
	    }
	}

	/**
	 * Opens a connection to the derby database represented with the File 'path'
	 * @param path Job directory where the derby db will be saved
	 * @return SQL Connection to the derby db
	 * @throws SQLException 
	 */
	public static Connection createDBConnection(File path) throws SQLException
	{
		String dbUrl = "jdbc:derby:" + path.getAbsolutePath().replace('\\', '/') + "/" + DERBY_DB_FOLDER + ";create=true;user=dbuser;password=dbuserpwd";
		Logger.debug("Attempting to open connection to database: " + path.getAbsolutePath() + IntegritySCM.FS + DERBY_DB_FOLDER);
	    return DriverManager.getConnection(dbUrl);
	}

	/**
	 * Shuts down the embedded derby database represented with the File 'path'
	 * @param path Job directory where the derby db can be located
	 */
	public static void shutdownDB(File path)
	{
		String dbUrl = "jdbc:derby:" + path.getAbsolutePath().replace('\\', '/') + "/" + DERBY_DB_FOLDER + ";shutdown=true;user=dbuser;password=dbuserpwd";
		try 
		{
			Logger.debug("Attempting to shut down database: " + path.getAbsolutePath() + IntegritySCM.FS + DERBY_DB_FOLDER);
		    Connection db = DriverManager.getConnection(dbUrl);
		    db.close();
		}
		catch( SQLException sqle )
		{
			Logger.error("Failed to shutdown database connection!");
			Logger.error(sqle.getMessage());
		    Logger.fatal(sqle);
		}		
	}
	
	/**
	 * Helper function that simply drops tables and indexes
	 * @param db Derby database connection
	 * @return true/false depending on the success of the operation
	 * @throws SQLException
	 */
	private static boolean dropTables(Connection db) throws SQLException
	{
		boolean tablesDropped = false;
		
		// First drop the Member_Name index
		Statement dropIndx = db.createStatement();
		tablesDropped = dropIndx.execute(DROP_NAME_INDEX);
		dropIndx.close();
		
		// Second drop the CM_Project table
		Statement dropTable = db.createStatement();
		tablesDropped = dropTable.execute(DROP_PROJECT_TABLE);
		dropTable.close();
		
		Logger.debug("Prior Integrity SCM cache tables successfully dropped!");		
		return tablesDropped;
	}
	
	/**
	 * Helper function that simply creates new tables and indexes
	 * @param db Derby database connection
	 * @return true/false depending on the success of the operation
	 * @throws SQLException
	 */
	private static boolean createTables(Connection db) throws SQLException
	{
		boolean tablesCreated = false;
		
		// First create the CM_Project table
		Statement createTable = db.createStatement();
		tablesCreated = createTable.execute(CREATE_PROJECT_TABLE);
		createTable.close();
		
		// Create an index on the Member Name column
		Statement createIndex = db.createStatement();
		tablesCreated = createIndex.execute(CREATE_NAME_INDEX);
		createIndex.close();
		
		Logger.debug("New Integrity SCM cache tables successfully created!");
		return tablesCreated;
		
	}

	/**
	 * Establishes a fresh set of Integrity SCM cache tables
	 * @param db Derby database connection
	 * @return true/false depending on the success of the operation
	 */
	public static boolean createCMProjectTables(Connection db)
	{
		boolean tablesCreated = false;
		Statement select = null;
		try
		{
			select = db.createStatement();
			if( select.execute(SELECT_MEMBER_1) )
			{
				try
				{
					Logger.debug("A prior set of Integrity SCM cache tables detected, dropping...");
					// Close the select statement, so that we can drop the table
					select.close();
					tablesCreated = dropTables(db);
					Logger.debug("Recreating a fresh set of Integrity SCM cache tables...");
					tablesCreated = createTables(db);
				}
				catch( SQLException ex )
				{
					Logger.error("Failed to create Integrity SCM cache tables!");
					Logger.fatal(ex);
					tablesCreated = false;
				}
			}
		} 
		catch( SQLException ex ) 
		{
			Logger.debug(ex.getMessage());
			try
			{
				Logger.debug("Integrity SCM cache tables do not exist, creating...");				
				tablesCreated = createTables(db);
			}
			catch( SQLException sqlex )
			{
				Logger.error("Failed to create Integrity SCM cache tables!");
				Logger.fatal(sqlex);
				tablesCreated = false;
			}
		}
		finally
		{
			if( null != select )
			{
				try 
				{
					select.close();
				} 
				catch( SQLException ex )
				{
					Logger.error(ex.getMessage());
					Logger.fatal(ex);
					tablesCreated = false;
				}
			}
		}
		
		return tablesCreated;
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
					value = rs.getBlob(i);
					if( ! rs.wasNull() )
					{
						InputStream is = rs.getBlob(i).getBinaryStream();
						try
						{
							byte[] bytes = IOUtils.toByteArray(is);
							rowData.put(getEnum(rsMetaData.getColumnLabel(i)), bytes);
						}
						finally
						{
							is.close();
						}
					}
					break;
					
				case java.sql.Types.BOOLEAN:
					value = rs.getBoolean(i);
					if( !rs.wasNull() ){ rowData.put(getEnum(rsMetaData.getColumnLabel(i)), rs.getBoolean(i)); }
					break;
					
				case java.sql.Types.CLOB:
					value = rs.getClob(i);
					if( ! rs.wasNull() )
					{
						BufferedReader reader = new java.io.BufferedReader(rs.getClob(i).getCharacterStream());
						String line = null;
						StringBuilder sb = new StringBuilder();
						try
						{
							while( null != (line=reader.readLine()) ){ sb.append(line + IntegritySCM.NL); }
							rowData.put(getEnum(rsMetaData.getColumnLabel(i)), sb.toString());
						}
						finally
						{
							reader.close();
						}
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
					value = rs.getObject(i);
					if( !rs.wasNull() ){ rowData.put(getEnum(rsMetaData.getColumnLabel(i)), rs.getObject(i)); }
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
}
