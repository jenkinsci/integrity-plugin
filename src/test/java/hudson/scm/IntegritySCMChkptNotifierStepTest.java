// $Id: $
// (c) Copyright 2015 by PTC Inc. All rights reserved.
//
// This Software is unpublished, valuable, confidential property of
// PTC Inc. Any use or disclosure of this Software without the express
// written permission of PTC Inc. is strictly prohibited.

package hudson.scm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import hudson.AbortException;
import hudson.model.StreamBuildListener;
import hudson.scm.api.command.CommandFactory;
import hudson.scm.api.command.IAPICommand;
import hudson.scm.api.session.APISession;
import hudson.scm.api.session.ISession;

/**
 * @author Author: asen
 * @version $Revision: $
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ CommandFactory.class, IntegrityConfigurable.class, APISession.class})
@PowerMockIgnore({"javax.crypto.*" })
public class IntegritySCMChkptNotifierStepTest extends AbstractIntegrityTestCase
{
    public IntegritySCMChkptNotifierStepTest()
    {
	ByteArrayOutputStream out = new ByteArrayOutputStream();
	buildListener = new StreamBuildListener(out);
    }
    
    IntegritySCMChkptNotifierStep integritySCMChkptNotifierStep;

    @Test (expected = AbortException.class)
    public void test_throwsExceptionIfAPINotReachable() throws InterruptedException, IOException{
	PowerMockito.mockStatic(APISession.class);
	ISession api = FakeAPISession.create(fakeConfigObj);
	IntegrityConfigurable fakeConfigObj = PowerMockito.mock(IntegrityConfigurable.class);
	integritySCMChkptNotifierStep = new IntegritySCMChkptNotifierStep(fakeConfigObj, configPath, "Test", "Test");
	integritySCMChkptNotifierStep.perform(null, null, null, buildListener);
    }
    
    @Test
    public void test_checkpoint() throws InterruptedException, IOException{
	
	IntegrityConfigurable fakeConfigObj = PowerMockito.mock(IntegrityConfigurable.class);
        
	IAPICommand commandMock = new MockAPICommand(fakeConfigObj, IAPICommand.CHECKPOINT_COMMAND);
	PowerMockito.mockStatic(CommandFactory.class);
	PowerMockito.mockStatic(APISession.class);
	ISession api = FakeAPISession.create(fakeConfigObj);
	Mockito.when(CommandFactory.createCommand(IAPICommand.CHECKPOINT_COMMAND, fakeConfigObj)).thenReturn(commandMock);
	Mockito.when(APISession.create(fakeConfigObj)).thenReturn(api);
	
	integritySCMChkptNotifierStep = new IntegritySCMChkptNotifierStep(fakeConfigObj, configPath, "Test", "Test");
	integritySCMChkptNotifierStep.perform(null, null, null, buildListener);
    }
}
