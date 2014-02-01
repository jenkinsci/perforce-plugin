package hudson.plugins.perforce;

import com.tek42.perforce.PerforceException;
import hudson.plugins.perforce.PerforceSCMHelper.WhereMapping;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import junit.framework.TestCase;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.ByteArrayInputStream;

public class PerforceSCMHelperTest extends TestCase {

	public void testComputeChangesPathFromViews() {
		List<String> views;
		String path;
		views = new ArrayList<String>();
		path = PerforceSCMHelper.computePathFromViews(views);
		assertEquals("", path);
		views.clear();
		views.add("//xyz/... //Brian-xyz-hudproj/xyz/...");
		views.add("//xyz3/... //Brian-xyz-hudproj/xyz3/...");
		path = PerforceSCMHelper.computePathFromViews(views);
		assertEquals(path, "//xyz/... //xyz3/... ");
		views.clear();
		views.add("-//xyz3/... //Brian-xyz-hudproj/xyz3/...");
		path = PerforceSCMHelper.computePathFromViews(views);
		assertEquals(
				"ignore exclusion paths, as they're not relevant for detecting changes",
				path, "");
		views.clear();
		views.add("\t//xyz3/... //Brian-xyz-hudproj/xyz3/...");
		path = PerforceSCMHelper.computePathFromViews(views);
		assertEquals("ignore leading tabs", path, "//xyz3/... ");
	}

	public void testProjectPathIsValid() {
		assertFalse("blank string", PerforceSCMHelper
				.projectPathIsValidForMultiviews(""));
		assertTrue("typical value", PerforceSCMHelper
				.projectPathIsValidForMultiviews("//..."));
		assertTrue("label", PerforceSCMHelper
				.projectPathIsValidForMultiviews("//...@mylabel"));
	}

        public void testWhereParser() throws PerforceException {
            byte testOutput[] = {
                (byte)0x7b, (byte)0x73, (byte)0x04, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x63, (byte)0x6f, (byte)0x64, (byte)0x65, (byte)0x73, (byte)0x04, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x73,
                (byte)0x74, (byte)0x61, (byte)0x74, (byte)0x73, (byte)0x09, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x64, (byte)0x65, (byte)0x70, (byte)0x6f, (byte)0x74, (byte)0x46, (byte)0x69, (byte)0x6c,
                (byte)0x65, (byte)0x73, (byte)0x2b, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x2f, (byte)0x2f, (byte)0x49, (byte)0x6e, (byte)0x73, (byte)0x74, (byte)0x61, (byte)0x6c, (byte)0x6c, (byte)0x2f,
                (byte)0x74, (byte)0x72, (byte)0x75, (byte)0x6e, (byte)0x6b, (byte)0x2f, (byte)0x49, (byte)0x6e, (byte)0x73, (byte)0x74, (byte)0x61, (byte)0x6c, (byte)0x6c, (byte)0x65, (byte)0x72, (byte)0x73,
                (byte)0x2f, (byte)0x62, (byte)0x75, (byte)0x69, (byte)0x6c, (byte)0x64, (byte)0x2e, (byte)0x70, (byte)0x72, (byte)0x6f, (byte)0x70, (byte)0x65, (byte)0x72, (byte)0x74, (byte)0x69, (byte)0x65,
                (byte)0x73, (byte)0x73, (byte)0x0a, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x63, (byte)0x6c, (byte)0x69, (byte)0x65, (byte)0x6e, (byte)0x74, (byte)0x46, (byte)0x69, (byte)0x6c, (byte)0x65,
                (byte)0x73, (byte)0x32, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x2f, (byte)0x2f, (byte)0x72, (byte)0x70, (byte)0x65, (byte)0x74, (byte)0x74, (byte)0x69, (byte)0x2f, (byte)0x49, (byte)0x6e,
                (byte)0x73, (byte)0x74, (byte)0x61, (byte)0x6c, (byte)0x6c, (byte)0x2f, (byte)0x74, (byte)0x72, (byte)0x75, (byte)0x6e, (byte)0x6b, (byte)0x2f, (byte)0x49, (byte)0x6e, (byte)0x73, (byte)0x74,
                (byte)0x61, (byte)0x6c, (byte)0x6c, (byte)0x65, (byte)0x72, (byte)0x73, (byte)0x2f, (byte)0x62, (byte)0x75, (byte)0x69, (byte)0x6c, (byte)0x64, (byte)0x2e, (byte)0x70, (byte)0x72, (byte)0x6f,
                (byte)0x70, (byte)0x65, (byte)0x72, (byte)0x74, (byte)0x69, (byte)0x65, (byte)0x73, (byte)0x73, (byte)0x04, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x70, (byte)0x61, (byte)0x74, (byte)0x68,
                (byte)0x73, (byte)0x40, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x2f, (byte)0x68, (byte)0x6f, (byte)0x6d, (byte)0x65, (byte)0x2f, (byte)0x72, (byte)0x70, (byte)0x65, (byte)0x74, (byte)0x74,
                (byte)0x69, (byte)0x2f, (byte)0x77, (byte)0x6f, (byte)0x72, (byte)0x6b, (byte)0x73, (byte)0x70, (byte)0x61, (byte)0x63, (byte)0x65, (byte)0x2f, (byte)0x49, (byte)0x6e, (byte)0x73, (byte)0x74,
                (byte)0x61, (byte)0x6c, (byte)0x6c, (byte)0x2f, (byte)0x74, (byte)0x72, (byte)0x75, (byte)0x6e, (byte)0x6b, (byte)0x2f, (byte)0x49, (byte)0x6e, (byte)0x73, (byte)0x74, (byte)0x61, (byte)0x6c,
                (byte)0x6c, (byte)0x65, (byte)0x72, (byte)0x73, (byte)0x2f, (byte)0x62, (byte)0x75, (byte)0x69, (byte)0x6c, (byte)0x64, (byte)0x2e, (byte)0x70, (byte)0x72, (byte)0x6f, (byte)0x70, (byte)0x65,
                (byte)0x72, (byte)0x74, (byte)0x69, (byte)0x65, (byte)0x73, (byte)0x30, (byte)0x7b, (byte)0x73, (byte)0x04, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x63, (byte)0x6f, (byte)0x64, (byte)0x65,
                (byte)0x73, (byte)0x04, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x73, (byte)0x74, (byte)0x61, (byte)0x74, (byte)0x73, (byte)0x09, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x64, (byte)0x65,
                (byte)0x70, (byte)0x6f, (byte)0x74, (byte)0x46, (byte)0x69, (byte)0x6c, (byte)0x65, (byte)0x73, (byte)0x24, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x2f, (byte)0x2f, (byte)0x49, (byte)0x6e,
                (byte)0x73, (byte)0x74, (byte)0x61, (byte)0x6c, (byte)0x6c, (byte)0x2f, (byte)0x74, (byte)0x72, (byte)0x75, (byte)0x6e, (byte)0x6b, (byte)0x2f, (byte)0x49, (byte)0x6e, (byte)0x73, (byte)0x74,
                (byte)0x61, (byte)0x6c, (byte)0x6c, (byte)0x65, (byte)0x72, (byte)0x73, (byte)0x2f, (byte)0x62, (byte)0x75, (byte)0x69, (byte)0x6c, (byte)0x64, (byte)0x2e, (byte)0x78, (byte)0x6d, (byte)0x6c,
                (byte)0x73, (byte)0x0a, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x63, (byte)0x6c, (byte)0x69, (byte)0x65, (byte)0x6e, (byte)0x74, (byte)0x46, (byte)0x69, (byte)0x6c, (byte)0x65, (byte)0x73,
                (byte)0x2b, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x2f, (byte)0x2f, (byte)0x72, (byte)0x70, (byte)0x65, (byte)0x74, (byte)0x74, (byte)0x69, (byte)0x2f, (byte)0x49, (byte)0x6e, (byte)0x73,
                (byte)0x74, (byte)0x61, (byte)0x6c, (byte)0x6c, (byte)0x2f, (byte)0x74, (byte)0x72, (byte)0x75, (byte)0x6e, (byte)0x6b, (byte)0x2f, (byte)0x49, (byte)0x6e, (byte)0x73, (byte)0x74, (byte)0x61,
                (byte)0x6c, (byte)0x6c, (byte)0x65, (byte)0x72, (byte)0x73, (byte)0x2f, (byte)0x62, (byte)0x75, (byte)0x69, (byte)0x6c, (byte)0x64, (byte)0x2e, (byte)0x78, (byte)0x6d, (byte)0x6c, (byte)0x73,
                (byte)0x04, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x70, (byte)0x61, (byte)0x74, (byte)0x68, (byte)0x73, (byte)0x39, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x2f, (byte)0x68, (byte)0x6f,
                (byte)0x6d, (byte)0x65, (byte)0x2f, (byte)0x72, (byte)0x70, (byte)0x65, (byte)0x74, (byte)0x74, (byte)0x69, (byte)0x2f, (byte)0x77, (byte)0x6f, (byte)0x72, (byte)0x6b, (byte)0x73, (byte)0x70,
                (byte)0x61, (byte)0x63, (byte)0x65, (byte)0x2f, (byte)0x49, (byte)0x6e, (byte)0x73, (byte)0x74, (byte)0x61, (byte)0x6c, (byte)0x6c, (byte)0x2f, (byte)0x74, (byte)0x72, (byte)0x75, (byte)0x6e,
                (byte)0x6b, (byte)0x2f, (byte)0x49, (byte)0x6e, (byte)0x73, (byte)0x74, (byte)0x61, (byte)0x6c, (byte)0x6c, (byte)0x65, (byte)0x72, (byte)0x73, (byte)0x2f, (byte)0x62, (byte)0x75, (byte)0x69,
                (byte)0x6c, (byte)0x64, (byte)0x2e, (byte)0x78, (byte)0x6d, (byte)0x6c, (byte)0x30
            };
            byte[] bigTestOutput = new byte[testOutput.length * 3];
            System.arraycopy(testOutput, 0, bigTestOutput, 0, testOutput.length);
            System.arraycopy(testOutput, 0, bigTestOutput, testOutput.length, testOutput.length);
            System.arraycopy(testOutput, 0, bigTestOutput, testOutput.length*2, testOutput.length);
            List<PerforceSCMHelper.WhereMapping> maps;
            maps = PerforceSCMHelper.parseWhereMapping(bigTestOutput);
            for(int i = 0; i<3; i++){
                assertEquals("//Install/trunk/Installers/build.properties", maps.get(i*2).getDepotPath());
                assertEquals("//rpetti/Install/trunk/Installers/build.properties", maps.get(i*2).getWorkspacePath());
                assertEquals("/home/rpetti/workspace/Install/trunk/Installers/build.properties", maps.get(i*2).getFilesystemPath());
                assertEquals("//Install/trunk/Installers/build.xml", maps.get(i*2+1).getDepotPath());
                assertEquals("//rpetti/Install/trunk/Installers/build.xml", maps.get(i*2+1).getWorkspacePath());
                assertEquals("/home/rpetti/workspace/Install/trunk/Installers/build.xml", maps.get(i*2+1).getFilesystemPath());
            }
        }

        public void testReadIntNegativeByte() throws java.io.IOException {
            byte test[] = {(byte)-106,(byte)0,(byte)0,(byte)0};
            ByteArrayInputStream bais = new ByteArrayInputStream(test);
            int result = PerforceSCMHelper.readInt(bais);
            assertEquals(150,result);
        }

        public void testMappingImplementation() {
            assertEquals("/home/jenkins/workspace/trunk/type/xml/test.xml",
                    PerforceSCMHelper.doMapping(
                    "//Install/.../*.%%1",
                    "/home/jenkins/workspace/.../type/%%1/*.%%1",
                    "//Install/trunk/test.xml"));
            assertEquals("//workspace/Install/trunk/test.xml",
                    PerforceSCMHelper.doMapping(
                    "//Install/...",
                    "//workspace/Install/...",
                    "//Install/trunk/test.xml"));
            assertEquals("/home/jenkins/workspace/trunk/test.xml",
                    PerforceSCMHelper.doMapping(
                    "//Install/...",
                    "/home/jenkins/workspace/...",
                    "//Install/trunk/test.xml"));
            assertEquals("/home/jenkins/workspace/test/trunk.xml",
                    PerforceSCMHelper.doMapping(
                    "//Install/%%1/%%2.xml",
                    "/home/jenkins/workspace/%%2/%%1.xml",
                    "//Install/trunk/test.xml"));
            assertEquals("/home/jenkins/workspace/trunk/test.xml",
                    PerforceSCMHelper.doMapping(
                    "//Install/.../*.xml",
                    "/home/jenkins/workspace/.../*.xml",
                    "//Install/trunk/test.xml"));
            assertEquals("/home/jenkins/workspace/trunk/SomeClass$Sub.class",
                    PerforceSCMHelper.doMapping(
                    "//Install/.../*.class",
                    "/home/jenkins/workspace/.../*.class",
                    "//Install/trunk/SomeClass$Sub.class"));
            assertEquals("/home/jenkins/workspace/trunk/SomeClass$Sub.class",
                    PerforceSCMHelper.doMapping(
                    "//Install/.../*$Sub.class",
                    "/home/jenkins/workspace/.../*$Sub.class",
                    "//Install/trunk/SomeClass$Sub.class"));
            assertEquals("/home/jenkins/workspace/trunk/SomeFile.xml",
                    PerforceSCMHelper.doMapping(
                    "//[S-B_Src]/.../SomeFile.xml",
                    "/home/jenkins/workspace/.../SomeFile.xml",
                    "//[S-B_Src]/trunk/SomeFile.xml"));
            assertEquals("/home/jenkins/workspace/trunk/SomeFile.xml",
                    PerforceSCMHelper.doMapping(
                    "//[-B_Src]/.../SomeFile.xml",
                    "/home/jenkins/workspace/.../SomeFile.xml",
                    "//[-B_Src]/trunk/SomeFile.xml"));
            assertEquals("/home/jenkins/workspace/trunk/SomeFile.xml",
                    PerforceSCMHelper.doMapping(
                    "//[S-_Src]/.../SomeFile.xml",
                    "/home/jenkins/workspace/.../SomeFile.xml",
                    "//[S-_Src]/trunk/SomeFile.xml"));
            assertEquals("/home/jenkins/workspace/trunk/SomeFile.xml",
                    PerforceSCMHelper.doMapping(
                    "//[-Src]/.../SomeFile.xml",
                    "/home/jenkins/workspace/.../SomeFile.xml",
                    "//[-Src]/trunk/SomeFile.xml"));
            assertEquals("/home/jenkins/workspace/trunk/SomeFile.xml",
                    PerforceSCMHelper.doMapping(
                    "//[Src]/.../SomeFile.xml",
                    "/home/jenkins/workspace/.../SomeFile.xml",
                    "//[Src]/trunk/SomeFile.xml"));
            assertEquals("/home/jenkins/workspace/trunk/SomeFile.xml",
                    PerforceSCMHelper.doMapping(
                    "//[]/.../SomeFile.xml",
                    "/home/jenkins/workspace/.../SomeFile.xml",
                    "//[]/trunk/SomeFile.xml"));
            assertEquals("/home/jenkins/workspace/[some-directory]/[some-file].xml",
                    PerforceSCMHelper.doMapping(
                    "//[]/.../[some-file].xml",
                    "/home/jenkins/workspace/.../[some-file].xml",
                    "//[]/[some-directory]/[some-file].xml"));
        }

}
