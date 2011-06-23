package hudson.plugins.perforce;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * @author Brian Westrich
 */
public final class PerforceSCMHelper {

    private static final String DEPOT_ROOT = "//";
    private static final String EXCLUSION_VIEW_PREFIX = "-";

    private PerforceSCMHelper() {
        // static methods, do not instantiate
    }

    /**
     * Generate a path for the changes command based on a workspaces views.
     *
     * @param views
     * @return
     */
    static String computePathFromViews(Collection<String> views) {

        StringBuilder path = new StringBuilder("");

        for (String view : views) {
            StringTokenizer columns = new StringTokenizer(view, " ");
            String leftColumn = columns.nextToken().trim();
            if (leftColumn.indexOf(EXCLUSION_VIEW_PREFIX + DEPOT_ROOT) != -1) {
                continue;
            }
            leftColumn = leftColumn.substring(leftColumn.indexOf(DEPOT_ROOT));
            path.append(leftColumn + " ");
        }

        return path.toString();
    }

    /**
     * Assuming there are multiple views, see whether the project path is valid.
     *
     * @param projectPath the project path specified by the user.
     *
     * @return true if valid, false if invalid
     */
    static boolean projectPathIsValidForMultiviews(String projectPath) {
        return projectPath.equals("//...") // root of depot ok
                || projectPath.indexOf('@') > -1; // labels ok {
    }

    static public class WhereMapping {
        private String depot;
        private String workspace;
        private String filesystem;

        public WhereMapping(String depot,String workspace,String filesystem){
            this.depot = depot;
            this.workspace = workspace;
            this.filesystem = filesystem;
        }

        public String getDepotPath() {
            return depot;
        }
        
        public String getFilesystemPath() {
            return filesystem;
        }

        public String getWorkspacePath() {
            return workspace;
        }
        
    }

    static public int readInt(byte[] bytes, int offset){
        int result=0;
        for (int i=offset; i<offset+4; i++) {
            result += (int) (bytes[i]&0xff) << (8*(i-offset));
        }
        return result;
    }

    static private Map<String,String> readPythonDictionary(byte[] dict){
        int counter = 0;
        Map<String,String> map = new HashMap<String,String>();
        if(dict[0] == '{' && dict[1] == 's'){
            //good to go
            counter = 1;
            while(counter < dict.length && dict[counter] != 0x30){
                //read in pairs
                String key,value;
                if(dict[counter] == 's'){
                    counter++;
                    key = readPythonString(dict,counter);
                    counter += key.length() + 4;
                } else {
                    //keys 'should' always be strings
                    return null;
                }
                if(dict[counter] == 's'){
                    counter++;
                    value = readPythonString(dict,counter);
                    counter += value.length() + 4;
                } else {
                    //don't know how to handle non-string objects yet
                    return null;
                }
                map.put(key, value);
            }
        } else {
            return null;
        }
        return map;
    }

    static private String readPythonString(byte[] bytes, int offset){
        int length = (int)readInt(bytes, offset);
        String result = new String(bytes, offset+4, length);
        return result;
    }

    static public WhereMapping parseWhereMapping(byte[] whereOutput){
        String depot;
        String workspace;
        String filesystem;
        Map<String,String> map = readPythonDictionary(whereOutput);
        depot = map.get("depotFile");
        workspace = map.get("clientFile");
        filesystem = map.get("path");
        return new WhereMapping(depot,workspace,filesystem);
    }

}
