package hudson.plugins.perforce;

import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.plugins.perforce.PerforceToolInstallation.DescriptorImpl;
import hudson.plugins.perforce.browsers.P4Web;
import hudson.scm.SCM;
import hudson.tools.ToolProperty;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.LocalData;

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
        		"user", "pass", "client", "port", "", "exe", "sysRoot",
        		"sysDrive", "label", "counter", "upstreamProject", "shared", "charset", "charset2", "user", false, true, true, true, true, true, false,
                        false, true, false, false, false, "${basename}", 0, -1, browser, "exclude_user", "exclude_file", true);
        scm.setProjectPath("path");
        project.setScm(scm);

        // config roundtrip
        submit(new WebClient().getPage(project,"configure").getFormByName("config"));

        // verify that the data is intact
        assertEqualBeans(scm, project.getScm(),
                "p4User,p4Client,p4Port,p4Label,projectPath,p4Exe,p4SysRoot,p4SysDrive,forceSync,alwaysForceSync,dontUpdateClient,createWorkspace,updateView,slaveClientNameFormat,lineEndValue,firstChange,p4Counter,updateCounterValue,exposeP4Passwd,useViewMaskForPolling,viewMask,useViewMaskForSyncing,p4Charset,p4CommandCharset,p4Stream,useStreamDepot,showIntegChanges,fileLimit");
        assertEquals("exclude_user", scm.getExcludedUsers());
        assertEquals("exclude_file", scm.getExcludedFiles());
        //assertEqualBeans(scm.getBrowser(),p.getScm().getBrowser(),"URL");
    }

    public void testConfigRoundtripWithNoSystemRoot() throws Exception {
	FreeStyleProject project = createFreeStyleProject();
        P4Web browser = new P4Web(new URL("http://localhost/"));
        PerforceSCM scm = new PerforceSCM(
                        "user", "pass", "client", "port", "", "exe", "",
                        "", "label", "counter", "upstreamProject", "shared", "charset", "charset2", "user", false, true, true, true, true, true, false,
                        false, true, false, false, false, "${basename}", 0, -1, browser, "exclude_user", "exclude_file", true);
        assertEquals("", scm.getP4SysDrive());
        assertEquals("", scm.getP4SysRoot());
        scm.setProjectPath("path");
        project.setScm(scm);

        // config roundtrip
        submit(new WebClient().getPage(project,"configure").getFormByName("config"));

        // verify that the data is intact
        assertEqualBeans(scm, project.getScm(),
                "p4SysRoot,p4SysDrive");
    }

    public void testConfigRoundtripWithStream() throws Exception {
        FreeStyleProject project = createFreeStyleProject();
        P4Web browser = new P4Web(new URL("http://localhost/"));
        PerforceSCM scm = new PerforceSCM(
        		"user", "pass", "client", "port", "", "exe", "sysRoot",
        		"sysDrive", "label", "counter", "upstreamProject", "shared", "charset", "charset2", "user", false, true, true, true, true, true, false,
                        false, true, false, false, false, "${basename}", 0, -1, browser, "exclude_user", "exclude_file", true);
        scm.setP4Stream("stream");
        scm.setUseStreamDepot(true);
        project.setScm(scm);

        // config roundtrip
        submit(new WebClient().getPage(project,"configure").getFormByName("config"));

        // verify that the data is intact
        assertEqualBeans(scm, project.getScm(),
                "p4User,p4Client,p4Port,p4Label,p4Exe,p4SysRoot,p4SysDrive,forceSync,alwaysForceSync,dontUpdateClient,createWorkspace,updateView,slaveClientNameFormat,lineEndValue,firstChange,p4Counter,updateCounterValue,exposeP4Passwd,useViewMaskForPolling,viewMask,useViewMaskForSyncing,p4Charset,p4CommandCharset,p4Stream,useStreamDepot");
        assertEquals("exclude_user", scm.getExcludedUsers());
        assertEquals("exclude_file", scm.getExcludedFiles());
        //assertEqualBeans(scm.getBrowser(),p.getScm().getBrowser(),"URL");
    }

    public void testConfigPasswordEnctyptionAndDecription() throws Exception {
        FreeStyleProject project = createFreeStyleProject();
        P4Web browser = new P4Web(new URL("http://localhost/"));
        PerforceToolInstallation tool = new PerforceToolInstallation("test_installation", "p4.exe", Collections.<ToolProperty<?>>emptyList());
        DescriptorImpl descriptor = (DescriptorImpl) Hudson.getInstance().getDescriptor(PerforceToolInstallation.class);
        descriptor.setInstallations(new PerforceToolInstallation[] { tool });
        descriptor.save();
        String password = "pass";
        PerforceSCM scm = new PerforceSCM(
        		"user", password, "client", "port", "", "test_installation", "sysRoot",
        		"sysDrive", "label", "counter", "upstreamProject", "shared", "charset", "charset2", "user", false, true, true, true, true, true, false,
                        false, true, false, false, false, "${basename}", 0, -1, browser, "exclude_user", "exclude_file", true);
        scm.setProjectPath("path");
        project.setScm(scm);

        // config roundtrip
        submit(new WebClient().getPage(project,"configure").getFormByName("config"));

        // verify that the data is intact
        assertEqualBeans(scm, project.getScm(),
                "p4User,p4Client,p4Port,p4Label,projectPath,p4Tool,p4SysRoot,p4SysDrive,forceSync,alwaysForceSync,dontUpdateClient,updateView,slaveClientNameFormat,lineEndValue,firstChange,p4Counter,updateCounterValue,exposeP4Passwd,useViewMaskForPolling,viewMask,useViewMaskForSyncing,p4Charset,p4CommandCharset,p4Stream,useStreamDepot,showIntegChanges,fileLimit");
        assertEquals("exclude_user", scm.getExcludedUsers());
        assertEquals("exclude_file", scm.getExcludedFiles());

        PerforcePasswordEncryptor encryptor = new PerforcePasswordEncryptor();
        String encryptedPassword = encryptor.encryptString(password);
        assertEquals(encryptedPassword, ((PerforceSCM)project.getScm()).getP4Passwd());
    }

    public void testDepotContainsUnencryptedPassword() throws Exception {
        FreeStyleProject project = createFreeStyleProject();
        P4Web browser = new P4Web(new URL("http://localhost/"));
        PerforceToolInstallation tool = new PerforceToolInstallation("test_installation", "p4.exe", Collections.<ToolProperty<?>>emptyList());
        DescriptorImpl descriptor = (DescriptorImpl) Hudson.getInstance().getDescriptor(PerforceToolInstallation.class);
        descriptor.setInstallations(new PerforceToolInstallation[] { tool });
        descriptor.save();
        String password = "pass";
        PerforceSCM scm = new PerforceSCM(
        		"user", password, "client", "port", "", "test_installation", "sysRoot",
        		"sysDrive", "label", "counter", "upstreamProject", "shared", "charset", "charset2", "user", false, true, true, true, true, true, false,
                        false, true, false, false, false, "${basename}", 0, -1, browser, "exclude_user", "exclude_file", true);
        scm.setProjectPath("path");
        project.setScm(scm);

        assertEquals(password, ((PerforceSCM)project.getScm()).getDepot(null, null, null, null, null).getPassword());
    }

    public void testConfigSaveReloadAndSaveDoesNotDoubleEncryptThePassword() throws Exception {
        FreeStyleProject project = createFreeStyleProject();
        P4Web browser = new P4Web(new URL("http://localhost/"));
        PerforceToolInstallation tool = new PerforceToolInstallation("test_installation", "p4.exe", Collections.<ToolProperty<?>>emptyList());
        DescriptorImpl descriptor = (DescriptorImpl) Hudson.getInstance().getDescriptor(PerforceToolInstallation.class);
        descriptor.setInstallations(new PerforceToolInstallation[] { tool });
        descriptor.save();
        String password = "pass";
        PerforceSCM scm = new PerforceSCM(
        		"user", password, "client", "port", "", "test_installation", "sysRoot",
        		"sysDrive", "label", "counter", "upstreamProject", "shared", "charset", "charset2", "user", false, true, true, true, true, true, false,
                        false, true, false, false, false, "${basename}", 0, -1, browser, "exclude_user", "exclude_file", true);
        scm.setProjectPath("path");
        project.setScm(scm);

        // config roundtrip
        submit(new WebClient().getPage(project,"configure").getFormByName("config"));
        submit(new WebClient().getPage(project,"configure").getFormByName("config"));

        // verify that the data is intact
        assertEqualBeans(scm, project.getScm(),
                "p4User,p4Client,p4Port,p4Label,projectPath,p4Tool,p4SysRoot,p4SysDrive,forceSync,alwaysForceSync,dontUpdateClient,updateView,slaveClientNameFormat,lineEndValue,firstChange,p4Counter,updateCounterValue,exposeP4Passwd,useViewMaskForPolling,viewMask,useViewMaskForSyncing,p4Charset,p4CommandCharset,p4Stream,useStreamDepot,showIntegChanges,fileLimit");
        assertEquals("exclude_user", scm.getExcludedUsers());
        assertEquals("exclude_file", scm.getExcludedFiles());

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
        assertViewParsesSame("//depot/pathwithoutspace/... \"//client/path with space/...\"");
        assertViewParsesSame("\"//depot/path with space/...\" //client/pathwithoutspace/...");
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
    
    public void testWindowsRemotePathName() throws Exception {
        assertEquals("\\\\somehost\\someshare", PerforceSCM.processPathName("\\\\somehost\\someshare",false));
    }

    public void testFilenameP4PatternMatcher() throws Exception {
        assertEquals(true, PerforceSCM.doesFilenameMatchP4Pattern(
                "//depot/somefile/testfile",
                "//depot/...",true));
        assertEquals(false, PerforceSCM.doesFilenameMatchP4Pattern(
                "//depot3/somefile/testfile",
                "//depot/...",true));
        assertEquals(true, PerforceSCM.doesFilenameMatchP4Pattern(
                "//depot/somefile/testfile",
                "//depot/.../testfile",true));
        assertEquals(true, PerforceSCM.doesFilenameMatchP4Pattern(
                "//depot/somefile/testfile",
                "//depot/*/testfile",true));
        assertEquals(true, PerforceSCM.doesFilenameMatchP4Pattern(
                "//depot/somefile/testfile",
                "//depot/some*/...",true));
        assertEquals(true, PerforceSCM.doesFilenameMatchP4Pattern(
                "//depot/somefile/testfile",
                "//depot/*file...",true));
        assertEquals(true, PerforceSCM.doesFilenameMatchP4Pattern(
                "//depot/somefile/testfile",
                "//depot/.../*",true));
        assertEquals(false, PerforceSCM.doesFilenameMatchP4Pattern(
                "//depot/somefile/testfile",
                "//depot/somefile/test",true));
        assertEquals(true, PerforceSCM.doesFilenameMatchP4Pattern(
                "//depot/somefile/testfile",
                "//depot/somefile/testfile",true));
        assertEquals(false, PerforceSCM.doesFilenameMatchP4Pattern(
                "//depot/somefile/testfile",
                "//depot/.../test",true));
        assertEquals(false, PerforceSCM.doesFilenameMatchP4Pattern(
                "//depot/somefile/testfile",
                "//depot/.../*test",true));
        assertEquals(false, PerforceSCM.doesFilenameMatchP4Pattern(
                "//depot/somefile/testfile",
                "//depot/.../file*",true));
        assertEquals(true, PerforceSCM.doesFilenameMatchP4Pattern(
                "//depot/SomeFile/testFile",
                "//depot/s.../testfile", false));
    }

    public void testFileInView() throws Exception {
        String projectPath = 
                "//depot/somefile/...\n"+
                "-//depot/somefile/excludedfile...\n"+
                "+//depot/somefile/excludedfile/readdedfile\n"+
                "//depot/someotherfile/...";
        assertEquals(false,PerforceSCM.isFileInView("//depot/somefile/excludedfile", projectPath, true));
        assertEquals(false,PerforceSCM.isFileInView("//depot/somefile/excludedfile/test", projectPath, true));
        assertEquals(false,PerforceSCM.isFileInView("//depot/notincluded", projectPath, true));
        assertEquals(true, PerforceSCM.isFileInView("//depot/somefile/excludedfile/readdedfile", projectPath, true));
        assertEquals(true, PerforceSCM.isFileInView("//depot/someotherfile/test", projectPath, true));
        assertEquals(true,PerforceSCM.isFileInView("//depot/somefile/file", projectPath, true));
    }
        
    /** Test migration from "p4Exe" field to tool installation.
     * 
     * @throws Exception
     */
    @LocalData
    public void testP4ExeMigration() throws Exception {
        DescriptorImpl descriptor = (DescriptorImpl) Hudson.getInstance().getDescriptor(PerforceToolInstallation.class);
        PerforceToolInstallation[] expected = new PerforceToolInstallation[] {
                new PerforceToolInstallation("c:\\program files\\perforce\\p4.exe", "c:\\program files\\perforce\\p4.exe", Collections.<ToolProperty<?>>emptyList()), 
                new PerforceToolInstallation("p4.exe", "p4.exe", Collections.<ToolProperty<?>>emptyList())
        };
        assertEquals(expected, descriptor.getInstallations());
    }
    
    public void testDepotContainsUnencryptedPasswordWithgetProperty() throws Exception {
        FreeStyleProject project = createFreeStyleProject();
        P4Web browser = new P4Web(new URL("http://localhost/"));
        PerforceToolInstallation tool = new PerforceToolInstallation("test_installation", "p4.exe", Collections.<ToolProperty<?>>emptyList());
        DescriptorImpl descriptor = (DescriptorImpl) Hudson.getInstance().getDescriptor(PerforceToolInstallation.class);
        descriptor.setInstallations(new PerforceToolInstallation[] { tool });
        descriptor.save();
        String password = "pass";
        PerforceSCM scm = new PerforceSCM(
                "user", password, "client", "port", "", "test_installation", "sysRoot",
                "sysDrive", "label", "counter", "upstreamProject", "shared", "charset", "charset2", "user", false, true, true, true, true, true, false,
                        false, false, true, false, false, "${basename}", 0, -1, browser, "exclude_user", "exclude_file", true);
        scm.setP4Stream("stream");
        project.setScm(scm);

        assertEquals(password, ((PerforceSCM)project.getScm()).getDepot(null, null, null, null, null).getProperty("P4PASSWD"));
    }

    /**
     * Helper method to check that PerforceToolInstallations match.
     * 
     * @param expected Expected PerforceToolInstallation
     * @param actual Actual PerforceToolInstallation
     */
    static void assertEquals(PerforceToolInstallation[] expected, PerforceToolInstallation[] actual) {
        assertEquals("Was expecting " + expected.length + " tool installations but got " + actual.length + " instead.", expected.length, actual.length);
        for (PerforceToolInstallation actualTool : actual) {
            boolean found = false;
            for (PerforceToolInstallation expectedTool : expected) {
                if (expectedTool.getName().equals(actualTool.getName()) && expectedTool.getHome().equals(actualTool.getHome())) {
                    found = true;
                    break;
                }
            }
            assertTrue("Was not expecting tool installation '" + actualTool.getName() + "'.", found);
        }
    }

    public void testP4UpstreamProjectRenaming() throws Exception {
        P4Web browser = new P4Web(new URL("http://localhost/"));
        PerforceToolInstallation tool = new PerforceToolInstallation("test_installation", "p4.exe", Collections.<ToolProperty<?>>emptyList());
        DescriptorImpl descriptor = (DescriptorImpl) Hudson.getInstance().getDescriptor(PerforceToolInstallation.class);
        descriptor.setInstallations(new PerforceToolInstallation[] { tool });
        descriptor.save();

        FreeStyleProject upstreamProject = createFreeStyleProject();
        PerforceSCM upstreamScm = new PerforceSCM(
                "user", "pass", "client", "port", "", "test_installation", "sysRoot",
                "sysDrive", null, null, null, "shared", "charset", "charset2", "user", false, true, true, true, true, true, false,
                        false, true, false, false, false, "${basename}", 0, -1, browser, "exclude_user", "exclude_file", true);
        upstreamScm.setProjectPath("path");
        upstreamProject.setScm(upstreamScm);
        
        FreeStyleProject downstreamProject = createFreeStyleProject();
        String oldName = upstreamProject.getName();
        PerforceSCM downstreamScm = new PerforceSCM(
                "user", "pass", "client", "port", "", "test_installation", "sysRoot",
                "sysDrive", null, null, oldName, "shared", "charset", "charset2", "user", false, true, true, true, true, true, false,
                        false, true, false, false, false, "${basename}", 0, -1, browser, "exclude_user", "exclude_file", true);
        downstreamScm.setProjectPath("path");
        downstreamProject.setScm(downstreamScm);

        // config roundtrip
        submit(new WebClient().getPage(upstreamProject,"configure").getFormByName("config"));
        submit(new WebClient().getPage(downstreamProject,"configure").getFormByName("config"));

        PerforceSCM scm = (PerforceSCM) downstreamProject.getScm();
        assertEquals(scm.p4UpstreamProject, oldName);
        
        String newName = "newName" + oldName;
        upstreamProject.renameTo(newName);

        scm = (PerforceSCM) downstreamProject.getScm();
        assertEquals(scm.p4UpstreamProject, newName);
    }
}
