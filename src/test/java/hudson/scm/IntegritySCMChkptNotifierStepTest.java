/*******************************************************************************
 * Contributors:
 *     PTC 2016
 *******************************************************************************/

package hudson.scm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Test;

import hudson.AbortException;
import hudson.model.StreamBuildListener;

/**
 * @author Author: asen
 * @version $Revision: $
 */
public class IntegritySCMChkptNotifierStepTest extends AbstractIntegrityTestCase
{
  public IntegritySCMChkptNotifierStepTest()
  {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    buildListener = new StreamBuildListener(out);
  }

  IntegritySCMChkptNotifierStep integritySCMChkptNotifierStep;

  @Test(expected = AbortException.class)
  public void test_throwsExceptionIfAPINotReachable() throws InterruptedException, IOException
  {
    /*PowerMockito.mockStatic(APISession.class);
    ISession api = FakeAPISession.create(fakeConfigObj);
    IntegrityConfigurable fakeConfigObj = PowerMockito.mock(IntegrityConfigurable.class);
    integritySCMChkptNotifierStep =
        new IntegritySCMChkptNotifierStep(fakeConfigObj, configPath, "Test", "Test");
    integritySCMChkptNotifierStep.perform(null, null, null, buildListener);*/
  }

  @Test
  public void test_checkpoint() throws InterruptedException, IOException
  {
    /*IntegrityConfigurable fakeConfigObj = PowerMockito.mock(IntegrityConfigurable.class);
    IAPICommand commandMock = new MockAPICommand(fakeConfigObj, IAPICommand.CHECKPOINT_COMMAND);
    PowerMockito.mockStatic(CommandFactory.class);
    PowerMockito.mockStatic(APISession.class);
    ISession api = FakeAPISession.create(fakeConfigObj);
    Mockito.when(CommandFactory.createCommand(IAPICommand.CHECKPOINT_COMMAND, fakeConfigObj))
        .thenReturn(commandMock);
    Mockito.when(APISession.create(fakeConfigObj)).thenReturn(api);
    integritySCMChkptNotifierStep =
        new IntegritySCMChkptNotifierStep(fakeConfigObj, configPath, "Test", "Test");
    integritySCMChkptNotifierStep.perform(null, null, null, buildListener);*/
  }
}
