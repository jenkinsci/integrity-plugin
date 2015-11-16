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
import org.powermock.api.easymock.annotation.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import hudson.model.StreamBuildListener;
import hudson.scm.api.command.CommandFactory;
import hudson.scm.api.command.IAPICommand;

/**
 * @author Author: asen
 * @version $Revision: $
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ CommandFactory.class, IntegrityConfigurable.class})
public class IntegritySCMChkptNotifierStepTest extends AbstractIntegrityTestCase
{
    public IntegritySCMChkptNotifierStepTest()
    {
	ByteArrayOutputStream out = new ByteArrayOutputStream();
	buildListener = new StreamBuildListener(out);
    }
    
    IntegritySCMChkptNotifierStep integritySCMChkptNotifierStep;

    //@Mock private IAPICommand commandMock;
    @Mock IntegrityConfigurable ic;
    
    //@Test (expected = AbortException.class)
    public void test_throwsExceptionIfAPINotReachable() throws InterruptedException, IOException{
	integritySCMChkptNotifierStep = new IntegritySCMChkptNotifierStep(ic, configPath, "Test", "Test");
	integritySCMChkptNotifierStep.perform(null, null, null, buildListener);
    }
    
    @Test
    public void test_checkpoint() throws InterruptedException, IOException{
	
	IAPICommand commandMock = new MockAPICommand(ic, IAPICommand.CHECKPOINT_COMMAND);
	PowerMockito.mockStatic(CommandFactory.class);
	Mockito.when(CommandFactory.createCommand(IAPICommand.CHECKPOINT_COMMAND, ic)).thenReturn(commandMock);
	
	integritySCMChkptNotifierStep = new IntegritySCMChkptNotifierStep(ic, configPath, "Test", "Test");
	integritySCMChkptNotifierStep.perform(null, null, null, buildListener);
    }
}
