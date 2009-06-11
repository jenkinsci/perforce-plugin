package hudson.plugins.perforce;

import hudson.FilePath;
import hudson.model.FreeStyleProject;
import hudson.plugins.perforce.browsers.P4Web;
import org.jvnet.hudson.test.HudsonTestCase;
import hudson.plugins.perforce.PerforcePasswordEncryptorTest;
import java.net.URL;


/**
 * @author Kohsuke Kawaguchi
 */
public class PerforceSCMTest extends HudsonTestCase {
    /**
     * Makes sure that the configuration survives the round-trip.
     */
    public void testConfigRoundtrip() throws Exception {
        FreeStyleProject project = createFreeStyleProject();
        P4Web browser = new P4Web(new URL("http://localhost/"));
        PerforceSCM scm = new PerforceSCM(
        		"user", "pass", "client", "port", "path", "exe", "sysRoot", 
        		"sysDrive", "label", true, true, true, 0, browser);
        project.setScm(scm);

        // config roundtrip
        submit(new WebClient().getPage(project,"configure").getFormByName("config"));

        // verify that the data is intact
        assertEqualBeans(scm,project.getScm(),"p4User,p4Client,p4Port,p4Label,projectPath,p4Exe,p4SysRoot,p4SysDrive,forceSync,dontRenameClient,updateView,firstChange");
        //assertEqualBeans(scm.getBrowser(),p.getScm().getBrowser(),"URL");
    }

    public void testConfigPasswordEnctyptionAndDecription() throws Exception {
        FreeStyleProject project = createFreeStyleProject();
        P4Web browser = new P4Web(new URL("http://localhost/"));
        String password = "pass";
        PerforceSCM scm = new PerforceSCM(
        		"user", password, "client", "port", "path", "exe", "sysRoot",
        		"sysDrive", "label", true, true, true, 0, browser);
        project.setScm(scm);

        // config roundtrip
        submit(new WebClient().getPage(project,"configure").getFormByName("config"));

        // verify that the data is intact
        assertEqualBeans(scm,project.getScm(),"p4User,p4Client,p4Port,p4Label,projectPath,p4Exe,p4SysRoot,p4SysDrive,forceSync,dontRenameClient,updateView,firstChange");

        PerforcePasswordEncryptor encryptor = new PerforcePasswordEncryptor();
        String encryptedPassword = encryptor.encryptString(password);
        assertEquals(encryptedPassword, scm.getP4Passwd());

    }

      public void testDepotContainsUnencryptedPassword() throws Exception {
        FreeStyleProject project = createFreeStyleProject();
        P4Web browser = new P4Web(new URL("http://localhost/"));
        String password = "pass";
        PerforceSCM scm = new PerforceSCM(
        		"user", password, "client", "port", "path", "exe", "sysRoot",
        		"sysDrive", "label", true, true, true, 0, browser);
               
        assertEquals(password, scm.getDepot(null, null).getPassword());
        assertEquals(password, scm.getDepot().getPassword());

    }

}

