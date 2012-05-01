package hudson.scm;

import org.apache.commons.logging.impl.SimpleLog;

/**
 * Helper class to control normal vs. debug logging a little more effectively 
 */
public final class Logger 
{
	/**
	 * Wrapper function around accessing SimpleLog from Apache Commons Logging utility
	 * @param level - Sets the log priority level
	 * @param msg - Object message to be sent to the SimpleLog
	 */
	private static final void log(int level, Object msg)
	{
		// Define the properties for the SimpleLog
		System.setProperty("org.apache.commons.logging.simplelog.showlogname", "true");
    	System.setProperty("org.apache.commons.logging.simplelog.showShortLogname", "false");
    	System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
    	System.setProperty("org.apache.commons.logging.simplelog.dateTimeFormat", "MMM dd, yyyy h:mm:ss a");

    	// Create the SimpleLog object 
    	SimpleLog logger = new SimpleLog("IntegritySCM");
		
    	// Log an appropriate message based on the priority level
    	switch(level)
		{
			case SimpleLog.LOG_LEVEL_DEBUG:
				logger.debug(msg);
				break;
			
			case SimpleLog.LOG_LEVEL_INFO:
				logger.info(msg);
				break;
			
			case SimpleLog.LOG_LEVEL_WARN:
				logger.info(msg);
				break;
				
			case SimpleLog.LOG_LEVEL_ERROR:
				logger.info(msg);
				break;
				
			case SimpleLog.LOG_LEVEL_FATAL:
				logger.info(msg);
				break;
				
			default:
				logger.info(msg);
		}
	}

	/**
	 * Public convenience function for logging a debug message
	 * @param msg - A debug message string
	 */
	public static final void debug(Object msg)
	{
		log(SimpleLog.LOG_LEVEL_DEBUG, msg);
	}
	
	/**
	 * Public convenience function for logging an informational message
	 * @param msg - An informational message string
	 */
	public static final void info(Object msg)
	{
		log(SimpleLog.LOG_LEVEL_INFO, msg);
	}

	/**
	 * Public convenience function for logging a warning message
	 * @param msg - A warning message string
	 */
	public static final void warn(Object msg)
	{
		log(SimpleLog.LOG_LEVEL_WARN, msg);
	}
	
	/**
	 * Public convenience function for logging an error message
	 * @param msg - An error message string
	 */
	public static final void error(Object msg)
	{
		log(SimpleLog.LOG_LEVEL_ERROR, msg);
	}

	/**
	 * Public convenience function for logging a fatal message
	 * @param msg - A fatal message string
	 */
	public static final void fatal(Object msg)
	{
		log(SimpleLog.LOG_LEVEL_FATAL, msg);
	}	
}
