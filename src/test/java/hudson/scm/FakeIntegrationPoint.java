// $Id: FakeIntegrationPoint.java 1.2 2015/03/30 20:19:12IST Reid, Randall (rreid) Exp  $
// (c) Copyright 2014 by PTC Inc. All rights reserved.
//
// This Software is unpublished, valuable, confidential property of
// PTC Inc. Any use or disclosure of this Software without the express
// written permission of PTC Inc. is strictly prohibited.

package hudson.scm;

import java.util.Iterator;

import com.mks.api.IntegrationPoint;
import com.mks.api.Session;
import com.mks.api.VersionNumber;
import com.mks.api.response.APIException;

public class FakeIntegrationPoint extends FakeObject implements IntegrationPoint {

    public static final String COMMON_SESSION = "COMMON_SESSION";
    
    public VersionNumber getAPIRequestVersion()
    {
        return null;
    }

    public String getHostname()
    {
        return "";
    }

    public int getPort()
    {
        return 0;
    }

    public boolean isClientIntegrationPoint()
    {
        return false;
    }

    public boolean isSecure()
    {
        return false;
    }

    public Session createNamedSession(String integrationID,
	    VersionNumber overrideRequestVersion) throws APIException
    {
	return null;
    }

    public Session createNamedSession(String integrationID,
	    VersionNumber overrideRequestVersion, String username,
	    String password) throws APIException
    {
	return null;
    }

    public Session createSession() throws APIException
    {
        return null;
    }

    public Session createSession(String username, String password)
    	throws APIException
    {
        return null;
    }

    public Session createSession(String username, String password,
    	int apiMajorVersion, int apiMinorVersion) throws APIException
    {
        return null;
    }

    public Session getCommonSession() throws APIException
    {
        return (Session) getValueByKey(COMMON_SESSION);	    
    }

    public Session getCommonSession(String username, String password)
    	throws APIException
    {
        return null;
    }

    public Iterator getSessions()
    {
        return null;
    }

    public boolean getAutoStartIntegrityClient()
    {
        return false;
    }

    public void setAutoStartIntegrityClient(boolean autoStartIC)
    {
    }

    public void release()
    {
    }
    
}