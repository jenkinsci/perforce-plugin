package hudson.plugins.perforce;

import hudson.model.FreeStyleProject;
import hudson.plugins.perforce.browsers.P4Web;
import org.jvnet.hudson.test.HudsonTestCase;

import java.net.URL;

/**
 * @author Kohsuke Kawaguchi
 */
public class PerforceSCMTest extends HudsonTestCase {
    /**
     * Makes sure that the configuration survives the round-trip.
     */
    public void testConfigRoundtrip() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        P4Web browser = new P4Web(new URL("http://localhost/"));
        PerforceSCM scm = new PerforceSCM(
        		"user", "pass", "client", "port", "path", "exe", "sysRoot", 
        		"sysDrive", "label", true, true, true, 0, browser);
        p.setScm(scm);

        // config roundtrip
        submit(new WebClient().getPage(p,"configure").getFormByName("config"));

        // verify that the data is intact        
        assertEqualBeans(scm,p.getScm(),
        		"p4User,p4Passwd,p4Client,p4Port,p4Exe,p4SysRoot,p4SysDrive," +
        		"p4Label,forceSync,updateView,renameClient,firstChange");
        assertEqualBeans(scm.getBrowser(),p.getScm().getBrowser(),"URL");
    }
}
