
package hudson.scm;

import java.util.HashSet;
import java.util.EnumMap;
import java.util.HashMap;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
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
import com.mks.api.response.Response;
import com.mks.api.response.WorkItem;
import com.mks.api.response.WorkItemIterator;
import com.mks.api.si.SIModelTypeName;

import hudson.AbortException;
import hudson.model.TaskListener;
import hudson.scm.IntegrityCMMember.CPInfo;
import hudson.scm.IntegrityCMMember.CPMember;
import hudson.scm.IntegritySCM.DescriptorImpl;
import hudson.scm.api.APIUtils;
import hudson.scm.api.ExceptionHandler;
import hudson.scm.api.command.CommandFactory;
import hudson.scm.api.command.IAPICommand;
import hudson.scm.api.option.APIOption;
import hudson.scm.api.option.IAPIFields;
import hudson.scm.api.option.IAPIOption;
import hudson.scm.api.option.IAPIFields.CP_MEMBER_OPERATION;

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
  public static final String CREATE_INTEGRITY_SCM_REGISTRY = "CREATE TABLE INTEGRITY_SCM_REGISTRY ("
      + "ID INTEGER NOT NULL PRIMARY KEY GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1), "
      + "JOB_NAME VARCHAR(256) NOT NULL, " + "CONFIGURATION_NAME VARCHAR(50) NOT NULL, "
      + "PROJECT_CACHE_TABLE VARCHAR(50) NOT NULL, " + "BUILD_NUMBER BIGINT NOT NULL)";
  public static final String SELECT_REGISTRY_1 =
      "SELECT ID FROM INTEGRITY_SCM_REGISTRY WHERE ID = 1";
  public static final String SELECT_REGISTRY_TABLE =
      "SELECT PROJECT_CACHE_TABLE FROM INTEGRITY_SCM_REGISTRY WHERE JOB_NAME = ? AND CONFIGURATION_NAME = ? AND BUILD_NUMBER = ?";
  public static final String SELECT_REGISTRY_TABLE_DROP =
      "SELECT PROJECT_CACHE_TABLE FROM INTEGRITY_SCM_REGISTRY WHERE JOB_NAME = ? AND BUILD_NUMBER = ?";
  public static final String INSERT_REGISTRY_ENTRY =
      "INSERT INTO INTEGRITY_SCM_REGISTRY (JOB_NAME, CONFIGURATION_NAME, PROJECT_CACHE_TABLE, BUILD_NUMBER) "
          + "VALUES (?, ?, ?, ?)";
  public static final String SELECT_REGISTRY_DISTINCT_PROJECTS =
      "SELECT DISTINCT JOB_NAME FROM INTEGRITY_SCM_REGISTRY";
  public static final String SELECT_REGISTRY_PROJECTS =
      "SELECT PROJECT_CACHE_TABLE FROM INTEGRITY_SCM_REGISTRY WHERE JOB_NAME = ? AND CONFIGURATION_NAME = ? ORDER BY BUILD_NUMBER DESC";
  public static final String SELECT_REGISTRY_PROJECT =
      "SELECT PROJECT_CACHE_TABLE FROM INTEGRITY_SCM_REGISTRY WHERE JOB_NAME = ?";
  public static final String DROP_REGISTRY_ENTRY =
      "DELETE FROM INTEGRITY_SCM_REGISTRY WHERE PROJECT_CACHE_TABLE = ?";
  public static final String CREATE_PROJECT_TABLE = "CREATE TABLE CM_PROJECT (" + CM_PROJECT.ID
      + " INTEGER NOT NULL " + "PRIMARY KEY GENERATED ALWAYS AS IDENTITY "
      + "(START WITH 1, INCREMENT BY 1), " + CM_PROJECT.TYPE + " SMALLINT NOT NULL, "
      + /* 0 = File; 1 = Directory */
      CM_PROJECT.NAME + " VARCHAR(32500) NOT NULL, " + CM_PROJECT.MEMBER_ID + " VARCHAR(32500), "
      + CM_PROJECT.TIMESTAMP + " TIMESTAMP, " + CM_PROJECT.DESCRIPTION + " CLOB(4 M), "
      + CM_PROJECT.AUTHOR + " VARCHAR(100), " + CM_PROJECT.CONFIG_PATH + " VARCHAR(32500), "
      + CM_PROJECT.REVISION + " VARCHAR(32500), " + CM_PROJECT.OLD_REVISION + " VARCHAR(32500), "
      + CM_PROJECT.RELATIVE_FILE + " VARCHAR(32500), " + CM_PROJECT.CHECKSUM + " VARCHAR(32), "
      + CM_PROJECT.DELTA + " SMALLINT, " + CM_PROJECT.CPID
      + " VARCHAR(32500) ) "; /* 0 = Unchanged; 1 = Added; 2 = Changed; 3 = Dropped */
  public static final String DROP_PROJECT_TABLE = "DROP TABLE CM_PROJECT";
  public static final String SELECT_MEMBER_1 =
      "SELECT " + CM_PROJECT.ID + " FROM CM_PROJECT WHERE " + CM_PROJECT.ID + " = 1";

  /** ------------ CP Cache tables -------------------- **/
  public static final String SELECT_CP_1 =
      "SELECT " + CM_PROJECT.ID + " FROM CM_PROJECT_CP WHERE " + CM_PROJECT.ID + " = 1";
  public static final String CREATE_PROJECT_CP_TABLE = "CREATE TABLE CM_PROJECT_CP ("
      + CM_PROJECT.ID + " INTEGER NOT NULL " + "PRIMARY KEY GENERATED ALWAYS AS IDENTITY "
      + "(START WITH 1, INCREMENT BY 1), " + CM_PROJECT.CPID + " VARCHAR(32500) NOT NULL, "
      + CM_PROJECT.CP_STATE + " VARCHAR(32500) NOT NULL)";
  public static final String INSERT_CP_RECORD = "INSERT INTO CM_PROJECT_CP " + "(" + CM_PROJECT.CPID
      + ", " + CM_PROJECT.CP_STATE + ") " + "VALUES (?, ?)";
  public static final String CP_SELECT =
      "SELECT " + CM_PROJECT.CPID + ", " + CM_PROJECT.CP_STATE + " FROM CM_PROJECT_CP";
  public static final String DELETE_CP_RECORD =
      "DELETE FROM CM_PROJECT_CP WHERE " + CM_PROJECT.CPID + " = ?";
  /** ------------ CP Cache tables end -------------------- **/

  public static final String INSERT_MEMBER_RECORD = "INSERT INTO CM_PROJECT " + "("
      + CM_PROJECT.TYPE + ", " + CM_PROJECT.NAME + ", " + CM_PROJECT.MEMBER_ID + ", "
      + CM_PROJECT.TIMESTAMP + ", " + CM_PROJECT.DESCRIPTION + ", " + CM_PROJECT.CONFIG_PATH + ", "
      + CM_PROJECT.REVISION + ", " + CM_PROJECT.RELATIVE_FILE + ", " + CM_PROJECT.CPID + ", "
      + CM_PROJECT.DELTA + ") " + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
  public static final String BASELINE_SELECT =
      "SELECT " + CM_PROJECT.NAME + ", " + CM_PROJECT.MEMBER_ID + ", " + CM_PROJECT.TIMESTAMP + ", "
          + CM_PROJECT.DESCRIPTION + ", " + CM_PROJECT.AUTHOR + ", " + CM_PROJECT.CONFIG_PATH + ", "
          + CM_PROJECT.REVISION + ", " + CM_PROJECT.RELATIVE_FILE + ", " + CM_PROJECT.CHECKSUM
          + " FROM CM_PROJECT WHERE " + CM_PROJECT.TYPE + " = 0 AND (" + CM_PROJECT.DELTA
          + " IS NULL OR " + CM_PROJECT.DELTA + " <> 3)";
  public static final String DELTA_SELECT =
      "SELECT " + CM_PROJECT.TYPE + ", " + CM_PROJECT.NAME + ", " + CM_PROJECT.MEMBER_ID + ", "
          + CM_PROJECT.TIMESTAMP + ", " + CM_PROJECT.DESCRIPTION + ", " + CM_PROJECT.AUTHOR + ", "
          + CM_PROJECT.CONFIG_PATH + ", " + CM_PROJECT.REVISION + ", " + CM_PROJECT.OLD_REVISION
          + ", " + CM_PROJECT.RELATIVE_FILE + ", " + CM_PROJECT.CHECKSUM + ", " + CM_PROJECT.DELTA
          + ", " + CM_PROJECT.CPID + " FROM CM_PROJECT WHERE " + CM_PROJECT.TYPE + " = 0";
  public static final String PROJECT_SELECT = "SELECT " + CM_PROJECT.NAME + ", "
      + CM_PROJECT.MEMBER_ID + ", " + CM_PROJECT.TIMESTAMP + ", " + CM_PROJECT.DESCRIPTION + ", "
      + CM_PROJECT.AUTHOR + ", " + CM_PROJECT.CONFIG_PATH + ", " + CM_PROJECT.REVISION + ", "
      + CM_PROJECT.OLD_REVISION + ", " + CM_PROJECT.RELATIVE_FILE + ", " + CM_PROJECT.CHECKSUM
      + ", " + CM_PROJECT.DELTA + ", " + CM_PROJECT.CPID + " FROM CM_PROJECT WHERE "
      + CM_PROJECT.TYPE + " = 0 ORDER BY " + CM_PROJECT.NAME + " ASC";

  public static final String SUB_PROJECT_SELECT = "SELECT " + CM_PROJECT.NAME + ", "
      + CM_PROJECT.CONFIG_PATH + ", " + CM_PROJECT.REVISION + " FROM CM_PROJECT WHERE "
      + CM_PROJECT.TYPE + " = 1 ORDER BY " + CM_PROJECT.CONFIG_PATH + " ASC";

  public static final String AUTHOR_SELECT = "SELECT " + CM_PROJECT.NAME + ", "
      + CM_PROJECT.MEMBER_ID + ", " + CM_PROJECT.AUTHOR + ", " + CM_PROJECT.CONFIG_PATH + ", "
      + CM_PROJECT.REVISION + " FROM CM_PROJECT WHERE " + CM_PROJECT.TYPE + " = 0 AND ("
      + CM_PROJECT.DELTA + " IS NULL OR " + CM_PROJECT.DELTA + " <> 3)";
  public static final String DIR_SELECT =
      "SELECT DISTINCT " + CM_PROJECT.RELATIVE_FILE + " FROM CM_PROJECT WHERE " + CM_PROJECT.TYPE
          + " = 1 ORDER BY " + CM_PROJECT.RELATIVE_FILE + " ASC";
  public static final String CHECKSUM_UPDATE = "SELECT " + CM_PROJECT.NAME + ", "
      + CM_PROJECT.CHECKSUM + " FROM CM_PROJECT WHERE " + CM_PROJECT.TYPE + " = 0 AND ("
      + CM_PROJECT.DELTA + " IS NULL OR " + CM_PROJECT.DELTA + " <> 3)";
  public static final String CP_MEMBER_SELECT = "SELECT " + CM_PROJECT.TYPE + ", " + CM_PROJECT.NAME
      + ", " + CM_PROJECT.MEMBER_ID + ", " + CM_PROJECT.TIMESTAMP + ", " + CM_PROJECT.DESCRIPTION
      + ", " + CM_PROJECT.AUTHOR + ", " + CM_PROJECT.CONFIG_PATH + ", " + CM_PROJECT.REVISION + ", "
      + CM_PROJECT.OLD_REVISION + ", " + CM_PROJECT.RELATIVE_FILE + ", " + CM_PROJECT.CHECKSUM
      + ", " + CM_PROJECT.DELTA + ", " + CM_PROJECT.CPID + " FROM CM_PROJECT WHERE "
      + CM_PROJECT.TYPE + " = 0 AND " + CM_PROJECT.NAME + " = ?";

  /**
   * Returns the CM_PROJECT column name for the string column name
   * 
   * @param name
   * @return
   */
  public static final CM_PROJECT getEnum(String name)
  {
    CM_PROJECT[] values = CM_PROJECT.values();
    for (int i = 0; i < values.length; i++)
    {
      if (name.equals(values[i].toString()))
      {
        return values[i];
      }
    }
    return CM_PROJECT.UNDEFINED;
  }

  /**
   * Random unique id generator for cache table names
   * 
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
    } catch (ClassNotFoundException ex)
    {
      LOGGER.severe("Failed to load derby driver: " + DERBY_DRIVER);
      LOGGER.severe(ex.getMessage());
      LOGGER.log(Level.SEVERE, "ClassNotFoundException", ex);
    }
  }

  /**
   * Creates a pooled connection data source for the derby database
   * 
   * @return
   */
  public static ConnectionPoolDataSource createConnectionPoolDataSource(String derbyHome)
  {
    EmbeddedConnectionPoolDataSource dataSource = new EmbeddedConnectionPoolDataSource();
    dataSource.setCreateDatabase("create");
    dataSource
        .setDataSourceName(DERBY_URL_PREFIX + derbyHome.replace('\\', '/') + "/" + DERBY_DB_NAME);
    dataSource.setDatabaseName(derbyHome.replace('\\', '/') + "/" + DERBY_DB_NAME);

    return dataSource;

  }

  /**
   * Generic SQL statement execution function
   * 
   * @param dataSource A pooled connection data source
   * @param sql String sql statement
   * @return
   * @throws SQLException
   */
  public static synchronized boolean executeStmt(ConnectionPoolDataSource dataSource, String sql)
      throws SQLException
  {
    boolean success = false;
    Connection db = null;
    PreparedStatement stmt = null;
    try
    {
      LOGGER.fine("Preparing to execute " + sql);
      db = dataSource.getPooledConnection().getConnection();
      stmt = db.prepareStatement(sql);
      success = stmt.execute();
      LOGGER.fine("Executed...!");
    } catch (SQLException sqlex)
    {
      throw sqlex;
    } finally
    {
      if (null != stmt)
      {
        stmt.close();
      }

      if (null != db)
      {
        db.close();
      }
    }

    return success;
  }

  /**
   * Creates the Integrity SCM cache registry table
   * 
   * @param dataSource
   * @return
   */
  public static synchronized boolean createRegistry(ConnectionPoolDataSource dataSource)
  {
    boolean tableCreated = false;
    try
    {
      if (executeStmt(dataSource, SELECT_REGISTRY_1))
      {
        LOGGER.fine("PTC RV&S SCM cache registry table exists...");
        tableCreated = true;
      }
    } catch (SQLException ex)
    {
      LOGGER.fine(ex.getMessage());
      try
      {
        LOGGER.fine("PTC RV&S SCM cache registry doesn't exist, creating...");
        tableCreated = executeStmt(dataSource, CREATE_INTEGRITY_SCM_REGISTRY);
      } catch (SQLException sqlex)
      {
        LOGGER.fine("Failed to create PTC RV&S SCM cache registry table!");
        LOGGER.log(Level.SEVERE, "SQLException", sqlex);
        tableCreated = false;
      }
    }

    return tableCreated;
  }

  /**
   * Creates a single Integrity SCM Project/Configuration cache table
   * 
   * @param dataSource
   * @param jobName
   * @param configurationName
   * @param buildNumber
   * @return
   * @throws SQLException
   */
  public static synchronized String registerProjectCache(ConnectionPoolDataSource dataSource,
      String jobName, String configurationName, long buildNumber) throws SQLException
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
      cacheTableName = getCachedTableFromRegistry("PROJECT_CACHE_TABLE", dataSource, jobName,
          configurationName, buildNumber);
      if (null == cacheTableName || cacheTableName.length() == 0)
      {
        // Insert a new row in the registry
        String uuid = getUUIDTableName();
        insert = db.prepareStatement(INSERT_REGISTRY_ENTRY);
        insert.clearParameters();
        insert.setString(1, jobName); // JOB_NAME
        insert.setString(2, configurationName); // CONFIGURATION_NAME
        insert.setString(3, uuid); // PROJECT_CACHE_TABLE
        insert.setLong(4, buildNumber); // BUILD_NUMBER
        insert.executeUpdate();
        cacheTableName = uuid;
      }
    } catch (SQLException sqlex)
    {
      LOGGER.fine(String.format("Failed to create PTC RV&S SCM cache registry entry for %s/%s/%d!",
          jobName, configurationName, buildNumber));
      LOGGER.log(Level.SEVERE, "SQLException", sqlex);
    } finally
    {
      if (null != select)
      {
        select.close();
      }
      if (null != rs)
      {
        rs.close();
      }
      if (null != insert)
      {
        insert.close();
      }
      if (null != db)
      {
        db.close();
      }
    }

    return cacheTableName;
  }

  /**
   * Returns the name of the project cache table for the specified job/configuration and build
   * 
   * @param dataSource
   * @param jobName
   * @param configurationName
   * @param buildNumber
   * @return
   * @throws SQLException
   */
  public static synchronized String getCachedTableFromRegistry(String tableName,
      ConnectionPoolDataSource dataSource, String jobName, String configurationName,
      long buildNumber) throws SQLException
  {
    String cacheTableName = "";
    Connection db = null;
    PreparedStatement select = null;
    PreparedStatement insert = null;
    ResultSet rs = null;

    try
    {
      db = dataSource.getPooledConnection().getConnection();
      select = db.prepareStatement(SELECT_REGISTRY_TABLE, ResultSet.TYPE_SCROLL_INSENSITIVE,
          ResultSet.CONCUR_READ_ONLY);
      select.setString(1, jobName);
      select.setString(2, configurationName);
      select.setLong(3, buildNumber);
      rs = select.executeQuery();
      if (getRowCount(rs) > 0)
      {
        rs.next();
        cacheTableName = rs.getString(tableName);
      }
    } catch (SQLException sqlex)
    {
      LOGGER.fine(String.format("Failed to get PTC RV&S SCM cache registry entry for %s/%s/%d!",
          jobName, configurationName, buildNumber));
      LOGGER.log(Level.SEVERE, "SQLException", sqlex);
    } finally
    {
      if (null != select)
      {
        select.close();
      }
      if (null != rs)
      {
        rs.close();
      }
      if (null != insert)
      {
        insert.close();
      }
      if (null != db)
      {
        db.close();
      }
    }

    return cacheTableName;
  }

  /**
   * Maintenance function that returns a list of distinct job names for additional checking to see
   * which ones are inactive
   * 
   * @param dataSource
   * @return
   * @throws SQLException
   */
  public static synchronized List<String> getDistinctJobNames(ConnectionPoolDataSource dataSource)
      throws SQLException
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
      while (rs.next())
      {
        String job = rs.getString("JOB_NAME");
        jobsList.add(job);
        LOGGER
            .fine(String.format("Adding job '%s' from the list of registered projects cache", job));
      }
    } catch (SQLException sqlex)
    {
      LOGGER.fine("Failed to run distinct jobs query!");
      LOGGER.log(Level.SEVERE, "SQLException", sqlex);
    } finally
    {
      if (null != select)
      {
        select.close();
      }
      if (null != rs)
      {
        rs.close();
      }
      if (null != delete)
      {
        delete.close();
      }
      if (null != db)
      {
        db.close();
      }
    }

    return jobsList;
  }

  /**
   * Maintenance function to delete all inactive project cache tables
   * 
   * @param dataSource
   * @param jobName
   * @throws SQLException
   */
  public static synchronized void deleteProjectCache(ConnectionPoolDataSource dataSource,
      String jobName) throws SQLException
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
      select = db.prepareStatement(SELECT_REGISTRY_PROJECT, ResultSet.TYPE_SCROLL_INSENSITIVE,
          ResultSet.CONCUR_READ_ONLY);
      select.setString(1, jobName);
      delete = db.prepareStatement(DROP_REGISTRY_ENTRY);
      rs = select.executeQuery();
      if (getRowCount(rs) > 0)
      {
        while (rs.next())
        {
          String cacheTableName = rs.getString("PROJECT_CACHE_TABLE");
          executeStmt(dataSource, DROP_PROJECT_TABLE.replaceFirst("CM_PROJECT", cacheTableName));
          delete.setString(1, cacheTableName);
          delete.addBatch();
        }

        delete.executeBatch();
      }
    } catch (SQLException sqlex)
    {
      LOGGER.fine("Failed to purge project '" + jobName + "' from PTC RV&S SCM cache registry!");
      LOGGER.log(Level.SEVERE, "SQLException", sqlex);
    } finally
    {
      if (null != select)
      {
        select.close();
      }
      if (null != rs)
      {
        rs.close();
      }
      if (null != delete)
      {
        delete.close();
      }
      if (null != db)
      {
        db.close();
      }
    }
  }

  /**
   * Maintenance function to limit project cache to the most recent builds
   * 
   * @param dataSource
   * @param jobName
   * @param buildNumber
   * @throws SQLException
   */
  public static synchronized void cleanupProjectCache(ConnectionPoolDataSource dataSource,
      String jobName, long buildNumber) throws SQLException
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
      select = db.prepareStatement(SELECT_REGISTRY_TABLE_DROP, ResultSet.TYPE_SCROLL_INSENSITIVE,
          ResultSet.CONCUR_READ_ONLY);
      select.setString(1, jobName);
      select.setLong(2, buildNumber);
      delete = db.prepareStatement(DROP_REGISTRY_ENTRY);
      rs = select.executeQuery();
      int rowCount = getRowCount(rs);
      int deleteCount = 0;
      LOGGER.fine("Cache entries for " + jobName + "/" + buildNumber + " = " + rowCount);
      while (rs.next())
      {
        String cacheTableName = rs.getString("PROJECT_CACHE_TABLE");
        LOGGER.fine(String.format("Deleting old cache entry for %s/%s", jobName, cacheTableName));
        try
        {
          // Attempting to drop the old cache table
          executeStmt(dataSource, DROP_PROJECT_TABLE.replaceFirst("CM_PROJECT", cacheTableName));
        } catch (SQLException sqlex)
        {
          // If this fails, then we'll have to investigate later...
          LOGGER.fine(String.format("Failed to drop table '%s' from PTC RV&S SCM cache registry!",
              cacheTableName));
          LOGGER.log(Level.SEVERE, "SQLException", sqlex);
        }

        // Tag the cache entry for removal
        deleteCount++;
        delete.setString(1, cacheTableName);
        delete.addBatch();
      }

      // Remove the cache entry regardless of the whether or not the cache table was purged
      if (deleteCount > 0)
      {
        delete.executeBatch();
      }
    } catch (SQLException sqlex)
    {
      LOGGER.fine(String.format(
          "Failed to clear old cache for project '%s' from PTC RV&S SCM cache registry!",
          jobName));
      LOGGER.log(Level.SEVERE, "SQLException", sqlex);
    } finally
    {
      if (null != select)
      {
        select.close();
      }
      if (null != rs)
      {
        rs.close();
      }
        delete.close();
        db.close();

    }
  }

  /**
   * Establishes a fresh set of Integrity SCM cache tables
   * 
   * @param db Derby database connection
   * @return true/false depending on the success of the operation
   */
  public static synchronized boolean createCMProjectTables(ConnectionPoolDataSource dataSource,
      String tableName)
  {
    boolean tableCreated = false;
    try
    {
      if (executeStmt(dataSource, SELECT_MEMBER_1.replaceFirst("CM_PROJECT", tableName)))
      {
        try
        {
          LOGGER.fine("A prior set of PTC RV&S SCM cache tables detected, dropping...");
          tableCreated =
              executeStmt(dataSource, DROP_PROJECT_TABLE.replaceFirst("CM_PROJECT", tableName));
          LOGGER.fine("Recreating a fresh set of PTC RV&S SCM cache tables...");
          tableCreated =
              executeStmt(dataSource, CREATE_PROJECT_TABLE.replaceFirst("CM_PROJECT", tableName));
        } catch (SQLException ex)
        {
          LOGGER.fine(
              String.format("Failed to create PTC RV&S SCM project cache table '%s'", tableName));
          LOGGER.log(Level.SEVERE, "SQLException", ex);
          tableCreated = false;
        }
      }
    } catch (SQLException ex)
    {
      LOGGER.fine(ex.getMessage());
      try
      {
        LOGGER.fine(
            String.format("PTC RV&S SCM cache table '%s' does not exist, creating...", tableName));
        tableCreated =
            executeStmt(dataSource, CREATE_PROJECT_TABLE.replaceFirst("CM_PROJECT", tableName));
      } catch (SQLException sqlex)
      {
        LOGGER.fine(
            String.format("Failed to create PTC RV&S SCM project cache table '%s'", tableName));
        LOGGER.log(Level.SEVERE, "SQLException", sqlex);
        tableCreated = false;
      }
    }

    return tableCreated;
  }

  /**
   * Create or return existing CP cache table
   * 
   * @param i
   * @param configurationName
   * 
   * @param db Derby database connection
   * @return true/false depending on the success of the operation
   */
  public static synchronized boolean getCPCacheTable(ConnectionPoolDataSource dataSource,
      String cpCacheTableName)
  {
    boolean tableCreated = false;
    try
    {
      if (executeStmt(dataSource, SELECT_CP_1.replaceFirst("CM_PROJECT_CP", cpCacheTableName)))
      {
        LOGGER.fine("A prior set of PTC RV&S SCM CP cache table for this job detected.");
      }
    } catch (SQLException ex)
    {
      LOGGER.fine(ex.getMessage());
      try
      {
        LOGGER.fine(String.format("PTC RV&S SCM CP cache table '%s' does not exist, creating...",
            cpCacheTableName));
        tableCreated = executeStmt(dataSource,
            CREATE_PROJECT_CP_TABLE.replaceFirst("CM_PROJECT_CP", cpCacheTableName));
      } catch (SQLException sqlex)
      {
        LOGGER.fine(String.format("Failed to create PTC RV&S SCM project CP cache table '%s'",
            cpCacheTableName));
        LOGGER.log(Level.SEVERE, "SQLException", sqlex);
        tableCreated = false;
      }
    }

    return tableCreated;
  }

  /**
  * Convenience function that converts a result set row into an EnumMap for easy access
   * 
   * @param rs ResultSet row object
  * @return EnumMap containing the non-null values for each column
   * @throws SQLException
   * @throws IOException
   */
  public static EnumMap<CM_PROJECT, Object> getRowData(ResultSet rs)
      throws SQLException, IOException
  {
  EnumMap<CM_PROJECT, Object> rowData = new EnumMap<>(CM_PROJECT.class);
    ResultSetMetaData rsMetaData = rs.getMetaData();
    int columns = rsMetaData.getColumnCount();
    for (int i = 1; i <= columns; i++)
    {
      int columnType = rsMetaData.getColumnType(i);
      @SuppressWarnings("unused")
      Object value = null;
      switch (columnType) {
        case java.sql.Types.ARRAY:
          value = rs.getArray(i);
          if (!rs.wasNull())
          {
            rowData.put(getEnum(rsMetaData.getColumnLabel(i)), rs.getArray(i));
          }
          break;

        case java.sql.Types.BIGINT:
        case java.sql.Types.NUMERIC:
        case java.sql.Types.REAL:
          value = rs.getLong(i);
          if (!rs.wasNull())
          {
            rowData.put(getEnum(rsMetaData.getColumnLabel(i)), rs.getLong(i));
          }
          break;

        case java.sql.Types.BLOB:
          try (InputStream is = rs.getBlob(i).getBinaryStream()) {
            byte[] bytes = IOUtils.toByteArray(is);
            rowData.put(getEnum(rsMetaData.getColumnLabel(i)), bytes);
          }
          break;

        case java.sql.Types.BOOLEAN:
          value = rs.getBoolean(i);
          if (!rs.wasNull())
          {
            rowData.put(getEnum(rsMetaData.getColumnLabel(i)), rs.getBoolean(i));
          }
          break;

        case java.sql.Types.CLOB:
          try (BufferedReader reader = new BufferedReader(rs.getClob(i).getCharacterStream())) {
            String line = null;
            StringBuilder sb = new StringBuilder();
            while (null != (line = reader.readLine())) {
              sb.append(line + IntegritySCM.NL);
            }
            rowData.put(getEnum(rsMetaData.getColumnLabel(i)), sb.toString());
          }
          break;

        case java.sql.Types.DATE:
          value = rs.getDate(i);
          if (!rs.wasNull())
          {
            rowData.put(getEnum(rsMetaData.getColumnLabel(i)), rs.getDate(i));
          }
          break;

        case java.sql.Types.DECIMAL:
          value = rs.getBigDecimal(i);
          if (!rs.wasNull())
          {
            rowData.put(getEnum(rsMetaData.getColumnLabel(i)), rs.getBigDecimal(i));
          }
          break;

        case java.sql.Types.DOUBLE:
          value = rs.getDouble(i);
          if (!rs.wasNull())
          {
            rowData.put(getEnum(rsMetaData.getColumnLabel(i)), rs.getDouble(i));
          }
          break;

        case java.sql.Types.FLOAT:
          value = rs.getFloat(i);
          if (!rs.wasNull())
          {
            rowData.put(getEnum(rsMetaData.getColumnLabel(i)), rs.getFloat(i));
          }
          break;

        case java.sql.Types.INTEGER:
          value = rs.getInt(i);
          if (!rs.wasNull())
          {
            rowData.put(getEnum(rsMetaData.getColumnLabel(i)), rs.getInt(i));
          }
          break;

        case java.sql.Types.JAVA_OBJECT:
          try
          {
            rowData.put(getEnum(rsMetaData.getColumnLabel(i)), rs.getObject(i));
          } finally
          {

          }
          break;

        case java.sql.Types.SMALLINT:
        case java.sql.Types.TINYINT:
          value = rs.getShort(i);
          if (!rs.wasNull())
          {
            rowData.put(getEnum(rsMetaData.getColumnLabel(i)), rs.getShort(i));
          }
          break;

        case java.sql.Types.TIME:
          value = rs.getTime(i);
          if (!rs.wasNull())
          {
            rowData.put(getEnum(rsMetaData.getColumnLabel(i)), rs.getTime(i));
          }
          break;

        case java.sql.Types.TIMESTAMP:
          value = rs.getTimestamp(i);
          if (!rs.wasNull())
          {
            rowData.put(getEnum(rsMetaData.getColumnLabel(i)), rs.getTimestamp(i));
          }
          break;

        default:
          value = rs.getString(i);
          if (!rs.wasNull())
          {
            rowData.put(getEnum(rsMetaData.getColumnLabel(i)), rs.getString(i));
          }
      }

    }

    return rowData;
  }

  /**
   * This function provides a count of the total number of rows in the ResultSet
   * 
   * @param set
   * @return
   * @throws SQLException
   */
  public static int getRowCount(ResultSet rs) throws SQLException
  {
    int rowCount = 0;
    int currentRow = rs.getRow();
    rowCount = rs.last() ? rs.getRow() : rowCount;
    if (currentRow == 0)
    {
      rs.beforeFirst();
    } else
    {
      rs.absolute(currentRow);
    }

    return rowCount;
  }

  /**
   * Attempts to fix known issues with characters that can potentially break the change log xml
   * 
   * @param desc Input comment string for the revision
   * @return Sanitized string that can be embedded within a CDATA tag
   */
  public static String fixDescription(String desc)
  {
    // Char 8211 which is a long dash causes problems for the change log XML, need to fix it!
    String description = desc.replace((char) 8211, '-');
    return description.replaceAll("<!\\[CDATA\\[", "< ! [ CDATA [").replaceAll("\\]\\]>", "] ] >");
  }

  /**
   * Compares this version of the project to a previous/new version to determine what are the
   * updates and what was deleted
   * 
   * @param baselineProjectCache The previous baseline (build) for this Integrity CM Project
   * @param return The total number of changes found in the comparison
   * @throws SQLException
   * @throws IOException
   */
  public static synchronized int compareBaseline(String serverConfigId, String baselineProjectCache,
      String projectCacheTable, Map<CPInfo, List<CPMember>> membersInCP, boolean skipAuthorInfo,
      boolean CPMode,TaskListener listener) throws SQLException, IOException
  {
    // Re-initialize our return variable
    int changeCount = 0;

  Connection db = null;
  PreparedStatement baselineSelect = null;
  PreparedStatement pjSelect = null;
  ResultSet baselineRS = null;
  ResultSet rs = null;
  PreparedStatement select = null;
  // Get a connection from our pool
  db = DescriptorImpl.INTEGRITY_DESCRIPTOR.getDataSource().getPooledConnection().getConnection();
  db.setAutoCommit(false);
    try {
      //listener.getLogger().println("S1:such nach error");
      if (CPMode) {
        //listener.getLogger().println("S2:such nach error");
        if (membersInCP.isEmpty()) return changeCount;
        String cpMemberSelectSql = CP_MEMBER_SELECT.replace("CM_PROJECT", projectCacheTable);
        select = db.prepareStatement(cpMemberSelectSql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE);
        for (CPInfo cpInfo : membersInCP.keySet()) {
          String cpid = cpInfo.getId();
          List<CPMember> cpMembers = membersInCP.get(cpInfo);
          for (CPMember cpMember : cpMembers) {
            String cpMemberName = cpMember.getMemberName();
            CP_MEMBER_OPERATION cpMemberOperation = cpMember.getOperationType();
            String cpMemberRevision = cpMember.getRevision();
            LOGGER.log(Level.FINE, "CP Member : " + cpMemberName + ", Type : " + cpMemberOperation.toString());

            select.setString(1, cpMemberName);
            rs = select.executeQuery();
            if (getRowCount(rs) > 0) {
              LOGGER.log(Level.FINE, "Retrieved member info from project cache for :" + cpMemberName);
              rs.next();
            }

            switch (cpMemberOperation) {
              case ADD:
                // TODO: Implementiere die Logik für ADD
                break;
              case DROP:
              case MOVEMEMBER:
                // TODO: Implementiere die Logik für DROP/MOVEMEMBER
                LOGGER.fine("... " + cpMemberName + " file operation: " + cpMemberOperation.toString() + " - revision was " + cpMemberRevision);
                break;
              case UPDATE:
                // TODO: Implementiere die Logik für UPDATE
                break;
              case RENAME:
                if (rs.getRow() == 0) {
                  rs.updateString(CM_PROJECT.CONFIG_PATH.toString(), cpMember.getLocation());
                  rs.updateString(CM_PROJECT.REVISION.toString(), cpMemberRevision);
                  rs.updateString(CM_PROJECT.RELATIVE_FILE.toString(), cpMember.getLocation());
                  rs.updateShort(CM_PROJECT.DELTA.toString(), (short) 3);
                  rs.updateString(CM_PROJECT.CPID.toString(), cpid);
                  rs.insertRow();
                  rs.moveToCurrentRow();
                } else {
                  rs.updateShort(CM_PROJECT.DELTA.toString(), (short) 2);
                  LOGGER.fine("... " + cpMemberName + " renamed - new revision is " + cpMemberRevision);
                  rs.updateRow();
                }
                break;
              case ADDFROMARCHIVE:
              case CREATESUBPROJECT:
                // NOOP
                break;
              default:
                LOGGER.log(Level.WARNING, "Unsupported CP Operation : " + cpMemberOperation.toString());
                break;
            }
          }
        }
        if (select != null) select.close();
      } else // File Mode comparison
      {
        //listener.getLogger().println("INFO: S1.2:such nach error: File Mode comparison");
        // Create the select statement for the previous baseline
        String baselineSelectSql = DerbyUtils.BASELINE_SELECT.replaceFirst("CM_PROJECT", baselineProjectCache);
        LOGGER.log(Level.FINE, "Attempting to execute query ", baselineSelectSql);
        baselineSelect = db.prepareStatement(baselineSelectSql);
        baselineRS = baselineSelect.executeQuery();

        // listener.getLogger().println("INFO: S1.3:such nach error: Create the select statement for the previous baseline");

    // Create a Map to hold the old baseline for easy comparison
    Map<String, EnumMap<CM_PROJECT, Object>> baselinePJ =
      new java.util.HashMap<>();
        while (baselineRS.next())
        {
    EnumMap<CM_PROJECT, Object> baselineRowHash = new EnumMap<>(CM_PROJECT.class);
    baselineRowHash.putAll(DerbyUtils.getRowData(baselineRS));
    EnumMap<CM_PROJECT, Object> memberInfo = new EnumMap<>(CM_PROJECT.class);
          memberInfo.put(CM_PROJECT.MEMBER_ID, (null == baselineRowHash.get(CM_PROJECT.MEMBER_ID)
              ? "" : baselineRowHash.get(CM_PROJECT.MEMBER_ID).toString()));
          memberInfo.put(CM_PROJECT.TIMESTAMP, (null == baselineRowHash.get(CM_PROJECT.TIMESTAMP)
              ? "" : (Date) baselineRowHash.get(CM_PROJECT.TIMESTAMP)));
          memberInfo.put(CM_PROJECT.DESCRIPTION,
              (null == baselineRowHash.get(CM_PROJECT.DESCRIPTION) ? ""
                  : baselineRowHash.get(CM_PROJECT.DESCRIPTION).toString()));
          memberInfo.put(CM_PROJECT.AUTHOR, (null == baselineRowHash.get(CM_PROJECT.AUTHOR) ? ""
              : baselineRowHash.get(CM_PROJECT.AUTHOR).toString()));
          memberInfo.put(CM_PROJECT.CONFIG_PATH,
              (null == baselineRowHash.get(CM_PROJECT.CONFIG_PATH) ? ""
                  : baselineRowHash.get(CM_PROJECT.CONFIG_PATH).toString()));
          memberInfo.put(CM_PROJECT.REVISION, (null == baselineRowHash.get(CM_PROJECT.REVISION) ? ""
              : baselineRowHash.get(CM_PROJECT.REVISION).toString()));
          memberInfo.put(CM_PROJECT.CPID, (null == baselineRowHash.get(CM_PROJECT.CPID) ? ""
              : baselineRowHash.get(CM_PROJECT.CPID).toString()));
          memberInfo.put(CM_PROJECT.RELATIVE_FILE,
              (null == baselineRowHash.get(CM_PROJECT.RELATIVE_FILE) ? ""
                  : baselineRowHash.get(CM_PROJECT.RELATIVE_FILE).toString()));
          memberInfo.put(CM_PROJECT.CHECKSUM, (null == baselineRowHash.get(CM_PROJECT.CHECKSUM) ? ""
              : baselineRowHash.get(CM_PROJECT.CHECKSUM).toString()));
          baselinePJ.put(baselineRowHash.get(CM_PROJECT.NAME).toString(), memberInfo);
        }

        // Create the select statement for the current project
        String pjSelectSql = DerbyUtils.DELTA_SELECT.replaceFirst("CM_PROJECT", projectCacheTable);
        LOGGER.log(Level.FINE, "Attempting to execute query ", pjSelectSql);
        pjSelect = db.prepareStatement(pjSelectSql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
        rs = pjSelect.executeQuery();

        listener.getLogger().println("INFO: S1.5: Create the select statement for the current project");

        // Now we will compare the adds and updates between the current project and the baseline
        for (int i = 1; i <= DerbyUtils.getRowCount(rs); i++)
        {
          //listener.getLogger().println("INFO: S1.6: compare current project and the baseline file by file");
          // Move the cursor to the current record
          rs.absolute(i);
          EnumMap<CM_PROJECT, Object> rowHash = DerbyUtils.getRowData(rs);
          // Obtain the member we're working with
          String memberName = rowHash.get(CM_PROJECT.NAME).toString();

          // Get the baseline project information for this member
          LOGGER.fine("Comparing file against baseline " + memberName);
          EnumMap<CM_PROJECT, Object> baselineMemberInfo = baselinePJ.get(memberName);

          // This file was in the previous baseline as well...
          if (null != baselineMemberInfo)
          {
            //listener.getLogger().println("INFO: S1.7: update member revision");
            // Did it change? Either by an update or roll back (update member revision)?
            String oldRevision = baselineMemberInfo.get(CM_PROJECT.REVISION).toString();
            if (!rowHash.get(CM_PROJECT.REVISION).toString().equals(oldRevision))
            {
              // Initialize the prior revision
              rs.updateString(CM_PROJECT.OLD_REVISION.toString(), oldRevision);
              // Initialize the author information as requested
              if (!skipAuthorInfo)
              {
                rs.updateString(CM_PROJECT.AUTHOR.toString(),
                    getAuthorFromRevisionInfo(serverConfigId,
                        rowHash.get(CM_PROJECT.CONFIG_PATH).toString(),
                        rowHash.get(CM_PROJECT.MEMBER_ID).toString(),
                        rowHash.get(CM_PROJECT.REVISION).toString()));
              }
              // Initialize the delta flag for this member
              rs.updateShort(CM_PROJECT.DELTA.toString(), (short) 2);
              LOGGER.fine("... " + memberName + " revision changed - new revision is "
                  + rowHash.get(CM_PROJECT.REVISION).toString());
              changeCount++;
            } else
            {
              //listener.getLogger().println("INFO: S1.7.2: old revision is Read catch");
              // This member did not change, so lets copy its old author information
              if (null != baselineMemberInfo.get(CM_PROJECT.AUTHOR))
              {
                rs.updateString(CM_PROJECT.AUTHOR.toString(),
                    baselineMemberInfo.get(CM_PROJECT.AUTHOR).toString());
              }
              // Also, lets copy over the previous MD5 checksum
              if (null != baselineMemberInfo.get(CM_PROJECT.CHECKSUM))
              {
                rs.updateString(CM_PROJECT.CHECKSUM.toString(),
                    baselineMemberInfo.get(CM_PROJECT.CHECKSUM).toString());
              }
              // Initialize the delta flag
              rs.updateShort(CM_PROJECT.DELTA.toString(), (short) 0);
            }

            // Remove this member from the baseline project HashMap, so we'll be left with items
            // that are dropped
            baselinePJ.remove(memberName);
          } else // We've found a new file
          {
            // Initialize the author information as requested
            if (!skipAuthorInfo)
            {
              rs.updateString(CM_PROJECT.AUTHOR.toString(),
                  getAuthorFromRevisionInfo(serverConfigId,
                      rowHash.get(CM_PROJECT.CONFIG_PATH).toString(),
                      rowHash.get(CM_PROJECT.MEMBER_ID).toString(),
                      rowHash.get(CM_PROJECT.REVISION).toString()));
            }
            // Initialize the delta flag for this member
            rs.updateShort(CM_PROJECT.DELTA.toString(), (short) 1);
            LOGGER.fine("... " + memberName + " new file - revision is " + rowHash.get(CM_PROJECT.REVISION).toString());
            //listener.getLogger().println("INFO S1.9: " + memberName + " new file - revision is " + rowHash.get(CM_PROJECT.REVISION).toString());
            changeCount++;
          }

          // Update this row in the data source
          rs.updateRow();
        }

        // Now, we should be left with the drops. Exist only in the old baseline and not the current
        // one.
        for (String memberName : baselinePJ.keySet()) {
          changeCount++;
          EnumMap<CM_PROJECT, Object> memberInfo = baselinePJ.get(memberName);

          // Add the deleted members to the database
          rs.moveToInsertRow();
          rs.updateShort(CM_PROJECT.TYPE.toString(), (short) 0);
          rs.updateString(CM_PROJECT.NAME.toString(), memberName);
          rs.updateString(CM_PROJECT.MEMBER_ID.toString(),
              memberInfo.get(CM_PROJECT.MEMBER_ID).toString());
          if (memberInfo.get(CM_PROJECT.TIMESTAMP) instanceof java.util.Date)
          {
            Timestamp ts = new Timestamp(((Date) memberInfo.get(CM_PROJECT.TIMESTAMP)).getTime());
            rs.updateTimestamp(CM_PROJECT.TIMESTAMP.toString(), ts);
          }
          rs.updateString(CM_PROJECT.DESCRIPTION.toString(),
              memberInfo.get(CM_PROJECT.DESCRIPTION).toString());
          rs.updateString(CM_PROJECT.AUTHOR.toString(),
              memberInfo.get(CM_PROJECT.AUTHOR).toString());
          rs.updateString(CM_PROJECT.CONFIG_PATH.toString(),
              memberInfo.get(CM_PROJECT.CONFIG_PATH).toString());
          rs.updateString(CM_PROJECT.REVISION.toString(),
              memberInfo.get(CM_PROJECT.REVISION).toString());
          rs.updateString(CM_PROJECT.RELATIVE_FILE.toString(),
              memberInfo.get(CM_PROJECT.RELATIVE_FILE).toString());
          rs.updateShort(CM_PROJECT.DELTA.toString(), (short) 3);
          // rs.updateString(CM_PROJECT.CPID.toString(),
          // memberInfo.get(CM_PROJECT.CPID).toString());
          rs.insertRow();
          rs.moveToCurrentRow();

          LOGGER.fine("... " + memberName + " file dropped - revision was "
              + memberInfo.get(CM_PROJECT.REVISION).toString());
        }
        // Commit changes to the database...
      }
        //listener.getLogger().println("INFO: S1.11: Commit changes to the database");
    } finally

    {
      // Close the result set and select statements
      if (null != rs)
      {
        rs.close();
      }
      if (null != pjSelect)
      {
        pjSelect.close();
      }
      baselineRS.close();
      baselineSelect.close();
      select.close();
      db.commit();
      db.setAutoCommit(true);
      db.close();
    }

    return changeCount;

  }

  /**
   * Updates the author information for all the members in the project
   * 
   * @throws SQLException
   * @throws IOException
   */
  public static synchronized void primeAuthorInformation(String serverConfigId,
      String projectCacheTable,TaskListener listener) throws SQLException, IOException
  {
    listener.getLogger().println("INFO: try to loging in sql DB");
    Connection db = DescriptorImpl.INTEGRITY_DESCRIPTOR.getDataSource().getPooledConnection().getConnection();
  PreparedStatement authSelect = db.prepareStatement(DerbyUtils.AUTHOR_SELECT.replace("CM_PROJECT", projectCacheTable), ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE);
    ResultSet rs = authSelect.executeQuery();
    db.setAutoCommit(false);
    try
      {
        listener.getLogger().println("PTC Plugin: Begin Prime Author Infomation (init or refresh complete history cache)...");
        listener.getLogger().println("PTC Plugin: Number of elements to process: ");

        int totalMembersInProjectTodo = 0;
        int filesDone = -1;
        int perc = -1; 
        if (rs.next()) {
            try {
                totalMembersInProjectTodo = rs.getInt(1);
                listener.getLogger().println("INFO: Total members in project: " + totalMembersInProjectTodo);
            } catch (SQLException e) {
                listener.getLogger().println("Warning: The value in the database is not a valid number. " + e.getMessage());
                totalMembersInProjectTodo = 1000;
            }
        } else {
            // Dieser Block wird ausgeführt, wenn das ResultSet leer ist, also keine Ergebnisse gefunden wurden.
            listener.getLogger().println("INFO: No SQL count available. The project seems to have no members.");
        }

      // Get a connection from our pool
      while (rs.next()) {
        
        filesDone += 10;      // report each 10 % percent only
        int pnew = (filesDone / totalMembersInProjectTodo);
        if (pnew > perc) {
            perc = pnew;
            listener.getLogger().println(perc*10 + "% done ...");
        } 
  EnumMap<CM_PROJECT, Object> rowHash = new EnumMap<>(CM_PROJECT.class);
  rowHash.putAll(DerbyUtils.getRowData(rs));
        rs.updateString(CM_PROJECT.AUTHOR.toString(),
                getAuthorFromRevisionInfo(serverConfigId,
                        rowHash.get(CM_PROJECT.CONFIG_PATH).toString(),
                        rowHash.get(CM_PROJECT.MEMBER_ID).toString(),
                        rowHash.get(CM_PROJECT.REVISION).toString()));
        rs.updateRow();
      } 

      // Commit the updates
      
    } finally{
      if (null != rs)
      {
        rs.close();
      }
      authSelect.close();
      db.commit();
      db.setAutoCommit(true);
      db.close();
    }
    // Release the result set

    // Release the statement

    // Close project db connections
  }


  /**
   * Updates the underlying Integrity SCM Project table cache with the new checksum information
   * 
   * @param checksumHash Checksum hashMap generated from a checkout operation
   * @throws SQLException
   * @throws IOException
   */
  public static synchronized void updateChecksum(String projectCacheTable,
      ConcurrentHashMap<String, String> checksumHash) throws SQLException, IOException
  {
    try (Connection db = DescriptorImpl.INTEGRITY_DESCRIPTOR.getDataSource().getPooledConnection()
            .getConnection(); PreparedStatement checksumSelect = db.prepareStatement(DerbyUtils.CHECKSUM_UPDATE.replaceFirst("CM_PROJECT", projectCacheTable), ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE); ResultSet rs = checksumSelect.executeQuery()) {
      db.setAutoCommit(false);
      // Get a connection from our pool
      // Create the select statement for the current project
      LOGGER.finer(String.format("Updating checksums for table: %s", rs.toString()));
      while (rs.next()) {
  EnumMap<CM_PROJECT, Object> rowHash = DerbyUtils.getRowData(rs);
        String newChecksum = checksumHash.get(rowHash.get(CM_PROJECT.NAME).toString());
        if (null != newChecksum && newChecksum.length() > 0) {
          LOGGER.finer(String.format("Updating checksum for rowHash: %s newChecksum: %s", rowHash.toString(), newChecksum));
          rs.updateString(CM_PROJECT.CHECKSUM.toString(), newChecksum);
          rs.updateRow();
        }
      }

      // Commit the updates
      db.commit();
      db.setAutoCommit(true);
      db.close();
    }
    // Release the result set

    // Release the statement

    // Close project db connections
  }

  /**
   * Project access function that returns the state of the current project NOTE: For maximum
   * efficiency, this should be called only once and after the compareBasline() has been invoked!
   * 
   * @return A List containing every member in this project, including any dropped artifacts
   * @throws SQLException
   * @throws IOException
   */
  public static synchronized List<EnumMap<CM_PROJECT, Object>> viewProject(
      String projectCacheTable) throws SQLException, IOException
  {
    // Initialize our return variable
    List<EnumMap<CM_PROJECT, Object>> projectMembersList =
        new ArrayList<>();

    // Initialize our db connection

    try (Connection db = DescriptorImpl.INTEGRITY_DESCRIPTOR.getDataSource().getPooledConnection()
            .getConnection(); PreparedStatement stmt = db.prepareStatement(DerbyUtils.PROJECT_SELECT.replaceFirst("CM_PROJECT", projectCacheTable)); ResultSet rs = stmt.executeQuery()) {
      // Get a connection from our pool
      while (rs.next()) {
        projectMembersList.add(DerbyUtils.getRowData(rs));
      }
    }
    // Close the database resources
    return projectMembersList;
  }

  /**
   * Project access function that returns the state of the current project NOTE: For maximum
   * efficiency, this should be called only once and after the compareBasline() has been invoked!
   * 
   * @return A List containing every subproject in this project
   * @throws SQLException
   * @throws IOException
   */
  public static synchronized List<EnumMap<CM_PROJECT, Object>> viewSubProjects(
      String projectCacheTable) throws SQLException, IOException
  {
    // Initialize our return variable
    List<EnumMap<CM_PROJECT, Object>> subprojectsList =
        new ArrayList<>();

    // Initialize our db connection

    try (Connection db = DescriptorImpl.INTEGRITY_DESCRIPTOR.getDataSource().getPooledConnection()
            .getConnection(); PreparedStatement stmt = db.prepareStatement(DerbyUtils.SUB_PROJECT_SELECT.replaceFirst("CM_PROJECT", projectCacheTable)); ResultSet rs = stmt.executeQuery()) {
      // Get a connection from our pool
      while (rs.next()) {
        subprojectsList.add(DerbyUtils.getRowData(rs));
      }
    }
    // Close the database resources

    return subprojectsList;
  }

  /**
   * Returns a string list of relative paths to all directories in this project
   * 
   * @return
   * @throws SQLException
   * @throws IOException
   */
  public static synchronized List<String> getDirList(String projectCacheTable)
      throws SQLException, IOException
  {
    // Initialize our return variable
    List<String> dirList = new ArrayList<String>();

    // Initialize our db connection

    try (Connection db = DescriptorImpl.INTEGRITY_DESCRIPTOR.getDataSource().getPooledConnection()
            .getConnection(); PreparedStatement stmt = db.prepareStatement(DerbyUtils.DIR_SELECT.replaceFirst("CM_PROJECT", projectCacheTable)); ResultSet rs = stmt.executeQuery()) {
      // Get a connection from our pool
      while (rs.next()) {
        EnumMap<CM_PROJECT, Object> rowData = DerbyUtils.getRowData(rs);
        dirList.add(rowData.get(CM_PROJECT.RELATIVE_FILE).toString());
      }
    }
    // Close the database resources

    return dirList;
  }

  /**
   * Cache the list of CPs (all states except "closed"). THis is to ensure that all CPs for a
   * project are tracked by Jenkins
   * 
   * @param cpCacheTable
   * @param cp
   * @param cpState
   * @param operation
   * @throws SQLException
   */
  public static Set<String> doCPCacheOperations(String cpCacheTable, String cp, String cpState,
      String operation) throws SQLException
  {
    Set<String> cachedCPIds = null;

    // Initialize our db connection
    Connection db = null;
    PreparedStatement stmt = null;
    PreparedStatement cachedCPSelect = null;
    ResultSet cachedCPRS = null;

    try
    {
      // Get a connection from our pool
      db = DescriptorImpl.INTEGRITY_DESCRIPTOR.getDataSource().getPooledConnection()
          .getConnection();
      db.setAutoCommit(false);
      if (operation.equalsIgnoreCase(IAPIFields.ADD_OPERATION))
      {
        // Add CP entry to cache
        String insertSQL = DerbyUtils.INSERT_CP_RECORD.replaceFirst("CM_PROJECT_CP", cpCacheTable);
        stmt = db.prepareStatement(insertSQL);
        stmt.clearParameters();
        stmt.setString(1, cp);
        stmt.setString(2, cpState);
        LOGGER.log(Level.FINE, "Updating CP Cache with CP : " + cp + ", State : " + cpState);
        stmt.executeUpdate();
      } else if (operation.equalsIgnoreCase(IAPIFields.DELETE_OPERATION))
      {
        // delete the CP entry from cache
        String deleteSQL = DerbyUtils.DELETE_CP_RECORD.replaceFirst("CM_PROJECT_CP", cpCacheTable);
        stmt = db.prepareStatement(deleteSQL);
        stmt.setString(1, cp);
        stmt.executeUpdate();

      } else if (operation.equalsIgnoreCase(IAPIFields.GET_OPERATION))
      {
        // Retrieve the list of CPs from the Derby DB
        cachedCPIds = new HashSet<String>();
        cachedCPSelect = db.prepareStatement(DerbyUtils.CP_SELECT.replaceFirst("CM_PROJECT_CP", cpCacheTable));
        cachedCPRS = cachedCPSelect.executeQuery();
        
        while (cachedCPRS.next())
        {
          cachedCPIds.add(cachedCPRS.getString(1));
        }
      } else
      {
        // Do nothing
        LOGGER.log(Level.SEVERE, "Operation :" + operation
            + " unsupported for updating CP Cache with CP : " + cp + ", State : " + cpState);
      }


    } finally
    {
      db.commit();
      db.setAutoCommit(true);
      stmt.close();
      db.close();
    } 
    return cachedCPIds;
  }
  
  /**
   * Performs a revision info on this Integrity Source File
   * 
   * @param configPath Full project configuration path
   * @param memberID Member ID for this file
   * @param memberRev Member Revision for this file
   * @return User responsible for making this change
   * @throws AbortException
   * @throws APICommandException
   */
  public static synchronized String getAuthorFromRevisionInfo(String serverConfigId, String configPath,
      String memberID, String memberRev) throws AbortException
  {
    String author = "unknown";

    // Construct the revision-info command
    IAPICommand command = CommandFactory.createCommand(IAPICommand.REVISION_INFO_COMMAND,
        DescriptorImpl.INTEGRITY_DESCRIPTOR.getConfiguration(serverConfigId));
    command.addOption(new APIOption(IAPIOption.PROJECT, configPath));
    command.addOption(new APIOption(IAPIOption.REVISION, memberRev));
    command.addSelection(memberID);

    Response response;
    try
    {
      response = command.execute();
      author = APIUtils.getAuthorInfo(response, memberID);

    } catch (APIException aex)
    {
      ExceptionHandler eh = new ExceptionHandler(aex);
      LOGGER.severe("API Exception caught...");
      LOGGER.severe(eh.getMessage());
      LOGGER.fine(eh.getCommand() + " returned exit code " + eh.getExitCode());
      aex.printStackTrace();
    }

    return author;
  }

      /**
     * Parses the output from the si viewproject command to get a list of
     * members
     *
     * @param siProject
     * @param wit WorkItemIterator
     * @param listener
     * @throws APIException
     * @throws SQLException
     */
    public static synchronized void parseProjectzf(IntegrityCMProject siProject, WorkItemIterator wit, TaskListener listener) throws APIException, SQLException {

        // Setup the Derby DB for this Project
        PreparedStatement insert = null;

                // Get a connection from our pool
        Connection db = DescriptorImpl.INTEGRITY_DESCRIPTOR.getDataSource().getPooledConnection().getConnection();
        try {

            // WABco: disable autocommit to avoid thousands of them while inserting into db
            db.setAutoCommit(false);

            // Create a fresh set of tables for this project
            listener.getLogger().println("Derby.parse:  Create a fresh set of tables for this project");
            DerbyUtils.createCMProjectTables(DescriptorImpl.INTEGRITY_DESCRIPTOR.getDataSource(), siProject.getProjectCacheTable());

            // Initialize the project config hash
            listener.getLogger().println("Derby.parse:  Initialize the project config hash");
            HashMap<String, String> pjConfigHash = new HashMap<>();
            // Add the mapping for the current project
            pjConfigHash.put(siProject.getProjectName(), siProject.getConfigurationPath());
            // Compute the project root directory
            String projectRoot = siProject.getProjectName().substring(0, siProject.getProjectName().lastIndexOf('/'));

            // Iterate through the list of members returned by the API
            String insertSQL = DerbyUtils.INSERT_MEMBER_RECORD.replaceFirst("CM_PROJECT", siProject.getProjectCacheTable());

            LOGGER.log(Level.FINE, "Attempting to execute query {0}", insertSQL);
            insert = db.prepareStatement(insertSQL);

            // Wabco:
            listener.getLogger().println("PTC viewproject - SCM Info:");

            while (wit.hasNext()) {
                WorkItem wi = wit.next();
                String entryType = (null != wi.getField("type") ? wi.getField("type").getValueAsString() : "");

                switch (wi.getModelType()) {
                    case SIModelTypeName.SI_SUBPROJECT:
                        // Ignore p e n d i n g subprojects in the tree...
                        if (entryType.equalsIgnoreCase("pending-sharesubproject")) {
                            LOGGER.log(Level.WARNING, "Skipping {0} {1}", new Object[]{entryType, wi.getId()});
                            listener.getLogger().println("Derby.parse:  Ignoring " + entryType + " " + wi.getId());
                        } else {
                            // Save the configuration path for the current subproject, using the canonical path name
                            pjConfigHash.put(wi.getField("name").getValueAsString(), wi.getId());
                            // Save the relative directory path for this subproject
                            String pjDir = wi.getField("name").getValueAsString().substring(projectRoot.length());
                            pjDir = pjDir.substring(0, pjDir.lastIndexOf('/'));
                            // Save this directory entry
                            insert.clearParameters();
                            insert.setShort(1, (short) 1);							// Type
                            insert.setString(2, wi.getField("name").getValueAsString());			// Name
                            insert.setString(3, wi.getId());							// MemberID
                            insert.setTimestamp(4, new Timestamp(Calendar.getInstance().getTimeInMillis()));	// Timestamp
                            insert.setClob(5, new StringReader(""));						// Description
                            insert.setString(6, wi.getId());							// ConfigPath
                            
                            String subProjectRev = "";
                            if (wi.contains("memberrev")) {
                                subProjectRev = wi.getField("memberrev").getItem().getId();
                            }
                            insert.setString(7, subProjectRev);							// Revision
                            insert.setString(8, pjDir);								// RelativeFile
                            
                            insert.executeUpdate();
                        }   break;
                    case SIModelTypeName.MEMBER:
                        // Ignore certain p e n d i n g operations
                        if (entryType.endsWith("in-pending-sub")
                                || entryType.equalsIgnoreCase("pending-add")
                                || entryType.equalsIgnoreCase("pending-move-to-update")
                                || entryType.equalsIgnoreCase("pending-rename-update")) {
                            LOGGER.log(Level.WARNING, "Skipping {0} {1}", new Object[]{entryType, wi.getId()});
                            listener.getLogger().println("Derby.parse:  Ignoring " + entryType + " " + wi.getId());
                        } else {
                            // Figure out this member's parent project's canonical path name
                            String parentProject = wi.getField("parent").getValueAsString();
                            // Save this member entry
                            String memberName = wi.getField("name").getValueAsString();
                            // Figure out the full member path
                            LOGGER.log(Level.FINE, "Member context: {0}", wi.getContext());
                            LOGGER.log(Level.FINE, "Member parent: {0}", parentProject);
                            LOGGER.log(Level.FINE, "Member name: {0}", memberName);
                            
                            // Process this member only if we can figure out where to put it in the workspace
                            if (memberName.startsWith(projectRoot)) {
                                String description = "";
                                // Per JENKINS-19791 some users are getting an exception when attempting
                                // to read the 'memberdescription' field in the API response. This is an
                                // attempt to catch the exception and ignore it...!
                                try {
                                    if (null != wi.getField("memberdescription") && null != wi.getField("memberdescription").getValueAsString()) {
                                        description = fixDescription(wi.getField("memberdescription").getValueAsString());
                                    }
                                } catch (NoSuchElementException e) {
                                    // Ignore exception
                                    LOGGER.log(Level.WARNING, "Cannot obtain the value for ''memberdescription'' in API response for member: {0}", memberName);
                                    LOGGER.info("API Response has the following fields available: ");
                                    for (@SuppressWarnings("unchecked")
                                    final Iterator<Field> fieldsIterator = wi.getFields(); fieldsIterator.hasNext();) {
                                        Field apiField = fieldsIterator.next();
                                        LOGGER.log(Level.INFO, "Name: {0}, Value: {1}", new Object[]{apiField.getName(), apiField.getValueAsString()});
                                    }
                                }
                                
                                Date timestamp = new Date();
                                // Per JENKINS-25068 some users are getting a null pointer exception when attempting
                                // to read the 'membertimestamp' field in the API response. This is an attempt to work around it!
                                try {
                                    Field timeFld = wi.getField("membertimestamp");
                                    if (null != timeFld && null != timeFld.getDateTime()) {
                                        timestamp = timeFld.getDateTime();
                                    }
                                } catch (Exception e) {
                                    // Ignore exception
                                    LOGGER.log(Level.WARNING, "Cannot obtain the value for ''membertimestamp'' in API response for member: {0}", memberName);
                                    LOGGER.log(Level.WARNING, "Defaulting ''membertimestamp'' to now - {0}", timestamp);
                                }
                                
                                insert.clearParameters();
                                insert.setShort(1, (short) 0);                                      // Type
                                insert.setString(2, memberName);                                    // Name
                                insert.setString(3, wi.getId());                                    // MemberID
                                insert.setTimestamp(4, new Timestamp(timestamp.getTime()));         // Timestamp
                                insert.setClob(5, new StringReader(description));                   // Description
                                insert.setString(6, pjConfigHash.get(parentProject));               // ConfigPath
                                insert.setString(7, wi.getField("memberrev").getItem().getId());    // Revision
                                insert.setString(8, memberName.substring(projectRoot.length()));    // RelativeFile (for workspace)
                                
                                // Wabco-Feature: always print all items and main configuration information to the log
                                listener.getLogger().println("\t"
                                        //									+ "Type:" +  (short)0                               // Type
                                        + memberName.substring(projectRoot.length()) + '\t' // RelativeFile (for workspace)
                                        //									+ memberName + "\t"                                 // Name
                                        + wi.getField("memberrev").getItem().getId() + '\t' // Revision
                                        //									+ wi.getId() + '\t'                                 // MemberID
                                        + new Timestamp(timestamp.getTime()) // Timestamp
                                        //									+ new StringReader(description)                     // Description
                                        //									+ pjConfigHash.get(parentProject)                   // ConfigPath
                                );
                                /// Wabco:
                                insert.executeUpdate();
                            } else {
                                // Issue warning...
                                LOGGER.log(Level.WARNING, "Skipping {0} it doesn''t appear to exist within this project {1}!", new Object[]{memberName, projectRoot});
                            }
                        }   break;
                    default:
                        LOGGER.log(Level.WARNING, "View project output contains an invalid model type: {0}", wi.getModelType());
                        break;
                }
            }

        } finally {
            if (null != insert) { insert.close(); }

            // Commit to the database
            db.commit();
            db.setAutoCommit(true);
            db.close();
        }

        // Log the completion of this operation
        LOGGER.log(Level.FINE, "Parsing project {0} complete!", siProject.getConfigurationPath());
    }

  
}
