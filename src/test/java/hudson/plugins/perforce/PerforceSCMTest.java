package hudson.plugins.perforce;

import hudson.model.FreeStyleProject;
import hudson.plugins.perforce.browsers.P4Web;
import org.apache.commons.beanutils.PropertyUtils;
import org.jvnet.hudson.test.HudsonTestCase;

import java.beans.PropertyDescriptor;
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
        PerforceSCM scm = new PerforceSCM("user", "pass", "client", "port", "path", "exe", "sysRoot", "sysDrive", true, true, 0, browser);
        p.setScm(scm);

        // config roundtrip
        submit(new WebClient().getPage(p,"configure").getFormByName("config"));

        // verify that the data is intact
        assertEqualBeans(scm,p.getScm(),"p4User,p4Passwd,p4Client,p4Port,projectPath,p4Exe,p4SysRoot,p4SysDrive,forceSync,updateView,firstChange");
        assertEqualBeans(scm.getBrowser(),p.getScm().getBrowser(),"URL");
    }

    // TODO: to be moved to HudsonTestCase
    private <T> void assertEqualBeans(T lhs, T rhs, String properties) throws Exception {
        for (String p : properties.split(",")) {
            PropertyDescriptor pd = PropertyUtils.getPropertyDescriptor(lhs, p);
            assertNotNull("No such property "+p+" on "+lhs.getClass(),pd);
            Object lp = PropertyUtils.getProperty(lhs, p);
            Object rp = PropertyUtils.getProperty(rhs, p);
            assertEquals("Property "+p+" is different",lp,rp);
        }
    }
}
