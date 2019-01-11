package hudson.scm.localclient;

import org.junit.After;
import org.junit.Before;

import com.mks.api.response.APIException;

/**
 * Created by amhaske on 09-01-2019.
 * Integration Tests for Local Client Testing with SSL enable Integrity Server
 */
public class SSLEnabledIntegrityNormalSandboxTaskTest extends IntegrityNormalSandboxTaskTest
{
	private static boolean store_ILMSecure_value = false;

    @Before
    public void setUp() throws Exception {
    	// Store secure connection flag value in variable 
    	store_ILMSecure_value = is_Secure_ILM_connection;
    	// Configure secure connection flag to true
    	is_Secure_ILM_connection = true;
        super.setUp();
    }

    @After
    public void cleanUp() throws APIException {
    	// Reconfigure secure connection to original value
    	is_Secure_ILM_connection = store_ILMSecure_value;
    	super.cleanUp();
    }
}