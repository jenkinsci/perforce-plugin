package hudson.plugins.perforce;

import hudson.model.FreeStyleProject;
import hudson.plugins.perforce.browsers.P4Web;

import java.net.URL;
import java.util.Arrays;
import java.util.List;

import org.jvnet.hudson.test.HudsonTestCase;

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
        		"user", "pass", "client", "port", "path", "", "exe", "sysRoot",
        		"sysDrive", "label", "counter", "shared", "charset", "charset2", false, true, true, true, false,
                        true, false, false, "${basename}", 0, browser);
        project.setScm(scm);

        // config roundtrip
        submit(new WebClient().getPage(project,"configure").getFormByName("config"));

        // verify that the data is intact
        assertEqualBeans(scm, project.getScm(),
                "p4User,p4Client,p4Port,p4Label,projectPath,p4Exe,p4SysRoot,p4SysDrive,forceSync,alwaysForceSync,dontUpdateClient,updateView,slaveClientNameFormat,lineEndValue,firstChange,p4Counter,updateCounterValue,exposeP4Passwd,useViewMaskForPolling,viewMask,useViewMaskForSyncing,p4Charset,p4CommandCharset");
        //assertEqualBeans(scm.getBrowser(),p.getScm().getBrowser(),"URL");
    }

    public void testConfigPasswordEnctyptionAndDecription() throws Exception {
        FreeStyleProject project = createFreeStyleProject();
        P4Web browser = new P4Web(new URL("http://localhost/"));
        String password = "pass";
        PerforceSCM scm = new PerforceSCM(
        		"user", password, "client", "port", "path", "", "exe", "sysRoot",
        		"sysDrive", "label", "counter", "shared", "charset", "charset2", false, true, true, true, false,
                        true, false, false, "${basename}", 0, browser);
        project.setScm(scm);

        // config roundtrip
        submit(new WebClient().getPage(project,"configure").getFormByName("config"));

        // verify that the data is intact
        assertEqualBeans(scm, project.getScm(),
                "p4User,p4Client,p4Port,p4Label,projectPath,p4Exe,p4SysRoot,p4SysDrive,forceSync,alwaysForceSync,dontUpdateClient,updateView,slaveClientNameFormat,lineEndValue,firstChange,p4Counter,updateCounterValue,exposeP4Passwd,useViewMaskForPolling,viewMask,useViewMaskForSyncing,p4Charset,p4CommandCharset");

        PerforcePasswordEncryptor encryptor = new PerforcePasswordEncryptor();
        String encryptedPassword = encryptor.encryptString(password);
        assertEquals(encryptedPassword, ((PerforceSCM)project.getScm()).getP4Passwd());
    }

    public void testDepotContainsUnencryptedPassword() throws Exception {
        FreeStyleProject project = createFreeStyleProject();
        P4Web browser = new P4Web(new URL("http://localhost/"));
        String password = "pass";
        PerforceSCM scm = new PerforceSCM(
        		"user", password, "client", "port", "path", "", "exe", "sysRoot",
        		"sysDrive", "label", "counter", "shared", "charset", "charset2", false, true, true, true, false,
                        true, false, false, "${basename}", 0, browser);

        project.setScm(scm);
        
        assertEquals(password, ((PerforceSCM)project.getScm()).getDepot(null, null, null).getPassword());
    }

    public void testConfigSaveReloadAndSaveDoesNotDoubleEncryptThePassword() throws Exception {
        FreeStyleProject project = createFreeStyleProject();
        P4Web browser = new P4Web(new URL("http://localhost/"));
        String password = "pass";
        PerforceSCM scm = new PerforceSCM(
        		"user", password, "client", "port", "path", "", "exe", "sysRoot",
        		"sysDrive", "label", "counter", "shared", "charset", "charset2", false, true, true, true, false,
                        true, false, false, "${basename}", 0, browser);
        project.setScm(scm);

        // config roundtrip
        submit(new WebClient().getPage(project,"configure").getFormByName("config"));
        submit(new WebClient().getPage(project,"configure").getFormByName("config"));
        
        // verify that the data is intact
        assertEqualBeans(scm, project.getScm(),
                "p4User,p4Client,p4Port,p4Label,projectPath,p4Exe,p4SysRoot,p4SysDrive,forceSync,alwaysForceSync,dontUpdateClient,updateView,slaveClientNameFormat,lineEndValue,firstChange,p4Counter,updateCounterValue,exposeP4Passwd,useViewMaskForPolling,viewMask,useViewMaskForSyncing,p4Charset,p4CommandCharset");

        PerforcePasswordEncryptor encryptor = new PerforcePasswordEncryptor();
        String encryptedPassword = encryptor.encryptString(password);
        assertEquals(encryptedPassword, ((PerforceSCM)project.getScm()).getP4Passwd());
    }

    static void assertViewParsesTo(String view, String toView) throws Exception {
        List<String> parsedPath = PerforceSCM.parseProjectPath(view, "client");
        assertTrue(PerforceSCM.equalsProjectPath(
                parsedPath,
                Arrays.asList(toView.split("\n"))));
    }

    static void assertViewParsesSame(String view) throws Exception {
        assertViewParsesTo(view, view);
    }

    static void assertViewParsesEmpty(String view) throws Exception {
        List<String> parsedPath = PerforceSCM.parseProjectPath(view, "client");
        assertTrue(parsedPath.isEmpty());
    }

    public void testViewParsingEmpties() throws Exception {
        assertViewParsesEmpty("");
        assertViewParsesEmpty("#comment");
        assertViewParsesEmpty("bad mapping");
        assertViewParsesEmpty("\"bad\" mapping");
    }

    public void testViewParsingSingles() throws Exception {
        assertViewParsesTo("//depot/path/...", "//depot/path/... //client/path/...");
        assertViewParsesTo("\"//depot/path/...\"", "\"//depot/path/...\" \"//client/path/...\"");
        assertViewParsesTo("-//depot/path/sub/...", "-//depot/path/sub/... //client/path/sub/...");
        assertViewParsesTo("+//depot/path/sub/...", "+//depot/path/sub/... //client/path/sub/...");
    }

    public void testViewParsingPairs() throws Exception {
        assertViewParsesSame("//depot/path/... //client/path/...");
        assertViewParsesSame("//depot/path/a/b/c/... //client/path/a/b/c/...");
        assertViewParsesSame("\"//depot/quotedpath/...\" \"//client/quotedpath/...\"");
        assertViewParsesSame("\"//depot/path with space/...\" \"//client/path with space/...\"");
        assertViewParsesSame("-//depot/path/sub/... //client/path/sub/...");
    }

    public void testViewParsingPairsAdjusted() throws Exception {
        assertViewParsesTo("//depot/path/... //xxx/path/...", "//depot/path/... //client/path/...");
        assertViewParsesTo(
                "\"//depot/path with space/...\" \"//xxx/path with space/...\"",
                "\"//depot/path with space/...\" \"//client/path with space/...\"");
    }

    public void testViewParsingMultiline() throws Exception {
        assertViewParsesTo(
                "//depot/path/...\n-//depot/path/sub/...\n\"//depot/path with space/...\"\n",
                "//depot/path/... //client/path/...\n" +
                    "-//depot/path/sub/... //client/path/sub/...\n" +
                    "\"//depot/path with space/...\" \"//client/path with space/...\"");
    }

    public void testUnixPathName() throws Exception {
        assertEquals("/Some/unix/path/", PerforceSCM.processPathName("//Some\\unix/./path/", true));
    }

    public void testWindowsPathName() throws Exception {
        assertEquals("C:\\Windows\\Path\\Name\\", PerforceSCM.processPathName("C://Windows\\.\\Path\\\\Name\\",false));
    }
}
