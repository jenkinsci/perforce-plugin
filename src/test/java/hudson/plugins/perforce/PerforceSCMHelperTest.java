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
            PerforceSCMHelper.WhereMapping map;
            map = PerforceSCMHelper.parseWhereMapping(testOutput);
            assertEquals("//Install/trunk/Installers/build.properties", map.getDepotPath());
            assertEquals("//rpetti/Install/trunk/Installers/build.properties", map.getWorkspacePath());
            assertEquals("/home/rpetti/workspace/Install/trunk/Installers/build.properties", map.getFilesystemPath());
        }

        public void testReadIntNegativeByte() {
            byte test[] = {(byte)-106,(byte)0,(byte)0,(byte)0};
            int result = PerforceSCMHelper.readInt(test, 0);
            assertEquals(150,result);
        }

        public void disabledtestWhereData() throws java.io.FileNotFoundException, java.io.IOException, PerforceException{
            File whereFile = new File("/home/rpetti/Downloads/whereData.dat");
            InputStream input = new FileInputStream(whereFile);
            int read=0;
            byte data[] = new byte[1024];
            ArrayList<Byte> bytes = new ArrayList<Byte>(1024);
            while((read = input.read(data, 0, 1024))!=-1){
                for(int i=0; i<read; i++){
                    bytes.add(new Byte((byte)(data[i]&0xff)));
                }
            }
            input.close();
            byte[] byteArray = new byte[bytes.size()];
            for(int i=0; i<bytes.size(); i++){
                byteArray[i] = bytes.get(i).byteValue();
            }
            PerforceSCMHelper.WhereMapping map;
            map = PerforceSCMHelper.parseWhereMapping(byteArray);
            System.out.println(map.getDepotPath() + " " + map.getFilesystemPath() + " " + map.getWorkspacePath());
        }

        public void disabledtestMappingExtensively() throws IOException, PerforceException {
            ArrayList<String> opts = new ArrayList<String>();
            opts.add("-c");
            opts.add("rpetti");
            opts.add("-p");
            opts.add("ratchet:1668");
            ArrayList<String> command = new ArrayList<String>();
            command.add("p4");
            command.addAll(opts);
            command.add("files");
            command.add("//DSConsole/...");
            Process p = Runtime.getRuntime().exec(command.toArray(new String[0]));
            p.getOutputStream().close();
            p.getErrorStream().close();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line=null;
            while((line = br.readLine()) != null){
                int hashLoc = line.indexOf("#");
                String filename = line.substring(0, hashLoc);
                System.out.println("Testing where on " + filename);
                ArrayList<String> whereCommand = new ArrayList<String>();
                whereCommand.add("p4");
                whereCommand.addAll(opts);
                whereCommand.add("-G");
                whereCommand.add("where");
                whereCommand.add(filename);
                Process whereProc = Runtime.getRuntime().exec(whereCommand.toArray(new String[0]));
                whereProc.getErrorStream().close();
                whereProc.getOutputStream().close();
                int read=0;
                byte data[] = new byte[1024];
                ArrayList<Byte> bytes = new ArrayList<Byte>(1024);
                while((read = whereProc.getInputStream().read(data, 0, 1024))!=-1){
                    for(int i=0; i<read; i++){
                        bytes.add(new Byte((byte)(data[i]&0xff)));
                    }
                }
                whereProc.getInputStream().close();
                byte[] byteArray = new byte[bytes.size()];
                for(int i=0; i<bytes.size(); i++){
                    byteArray[i] = bytes.get(i).byteValue();
                }
                PerforceSCMHelper.WhereMapping map;
                map = PerforceSCMHelper.parseWhereMapping(byteArray);
                System.out.println(map.getDepotPath() + " " + map.getFilesystemPath() + " " + map.getWorkspacePath());
            }
            br.close();
        }

}
