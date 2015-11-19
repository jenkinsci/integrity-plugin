// $Id: $
// (c) Copyright 2015 by PTC Inc. All rights reserved.
//
// This Software is unpublished, valuable, confidential property of
// PTC Inc. Any use or disclosure of this Software without the express
// written permission of PTC Inc. is strictly prohibited.

package hudson.scm.api.session;

import java.util.Hashtable;
import java.util.Map.Entry;

import hudson.scm.IntegrityConfigurable;

/**
 *
 * @author Author: asen
 * @version $Revision: $
 */
public class SessionTable
{
    private static Hashtable<IntegrityConfigurable, ISession> availableSessions = new Hashtable<IntegrityConfigurable, ISession>();
    
    /**
     * Adds a unique IntegrityConf session in the table
     * 
     * @param serverConfig
     * @param session
     */
    public static void addSession(IntegrityConfigurable serverConfig, ISession session){
	availableSessions.put(serverConfig, session);
    }
    
    /**
     * Gets an available IntegrityConf session
     * 
     * @param serverConfig
     * @return
     */
    public static ISession getSession(IntegrityConfigurable serverConfig){
	return availableSessions.get(serverConfig);
    }
    
    /**
     *  Terminate all sessions in the table and clear the session table
     */
    public static void clearSessions(){
	for(Entry<IntegrityConfigurable, ISession> session : availableSessions.entrySet()){
	    session.getValue().terminate();
	}
	availableSessions.clear();
    }

    public static String printKeys()
    {
	StringBuffer str = new StringBuffer();
	for(Entry<IntegrityConfigurable, ISession> session : availableSessions.entrySet()){
	    str.append(session.getKey().toString());
	}
	return str.toString();
    }
}
