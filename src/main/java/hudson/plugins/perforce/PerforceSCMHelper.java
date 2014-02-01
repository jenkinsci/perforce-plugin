package hudson.plugins.perforce;

import com.tek42.perforce.PerforceException;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.regex.*;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

/**
 * @author Brian Westrich
 */
public final class PerforceSCMHelper {

    private static final Logger LOGGER = Logger.getLogger(PerforceSCMHelper.class.getName());
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

    static public int readInt(InputStream stream) throws IOException {
        int result=0;
        for (int i=0; i<4; i++) {
            result += (int) (stream.read()&0xff) << (8*(i));
        }
        return result;
    }

    static private Map<String,String> readPythonDictionary(InputStream stream) throws IOException {
        int counter = 0;
        Map<String,String> map = new HashMap<String,String>();
        if(stream.read() == '{'){
            //good to go
            int b = stream.read();
            while(b != -1 && b != 0x30){
                //read in pairs
                String key,value;
                if(b == 's'){
                    key = readPythonString(stream);
                    b = stream.read();
                } else {
                    //keys 'should' always be strings
                    throw new IOException ("Expected 's', but got '" + Integer.toString(b) + "'.");
                }
                if(b == 's'){
                    value = readPythonString(stream);
                    b = stream.read();
                } else if(b == 'i'){
                    value = Integer.toString(readInt(stream));
                    b = stream.read();
                } else {
                    // Don't know how to handle anything but ints and strings, so bail out
                    throw new IOException ("Expected 's' or 'i', but got '" + Integer.toString(b) + "'.");
                }
                map.put(key, value);
            }
        } else {
            return null;
        }
        return map;
    }

    static private String readPythonString(InputStream stream) throws IOException {
        int length = (int)readInt(stream);
        byte[] buf = new byte[length];
        stream.read(buf, 0, length);
        String result = new String(buf);
        return result;
    }

    static public List<WhereMapping> parseWhereMapping(byte[] whereOutput) throws PerforceException {
        String depot;
        String workspace;
        String filesystem;
        ByteArrayInputStream stream = new ByteArrayInputStream(whereOutput);
        ArrayList<WhereMapping> maps = new ArrayList<WhereMapping>();
        Map<String,String> map;
        try{
            while((map = readPythonDictionary(stream)) != null) {
                if(map.get("code").equals("error")){
                    //error handling
                    LOGGER.log(Level.FINE, "P4 Where Parsing Error: "+map.get("data"));
                    if(map.get("data")!=null){
                        if(map.get("data").contains("not in client view")){
                            //this is non-fatal, but not sure what to do with it
                        } else {
                            throw new PerforceException("P4 Where Parsing Error: "+map.get("data"));
                        }
                    }
                }
                if(map.get("depotFile") == null || map.get("clientFile") == null || map.get("path") == null){
                    //not a valid mapping for some reason...
                    //possibly because some versions of perforce return the wrong values
                    LOGGER.log(Level.WARNING, "P4 Where returned unexpected output! Check to make sure your perforce client and server versions are up to date!");
                    continue;
                }
                depot = map.get("depotFile");
                workspace = map.get("clientFile");
                filesystem = map.get("path");

                maps.add(new WhereMapping(depot,workspace,filesystem));
            }
        } catch (IOException e) {
            throw new PerforceException("Could not parse Where map.", e);
        }
        return maps;
    }

    static public String mapToWorkspace(List<WhereMapping> maps, String depotPath) {
        String result=null;
        for(WhereMapping map : maps){
            if(doesPathMatchView(depotPath,map.getDepotPath())){
                if(map.getDepotPath().startsWith("-"))
                {
                    result=null;
                } else {
                    result = doMapping(map.getDepotPath(), map.getWorkspacePath(), depotPath);
                }
            }
        }
        return result;
    }

    static public boolean doesPathMatchView(String path, String view) {
        view = trimPlusMinus(view);
        Pattern pattern = getTokenPattern(view);
        Matcher matcher = pattern.matcher(path);
        if(matcher.matches()){
            return true;
        } else {
            return false;
        }
    }

    static public String trimPlusMinus(String str) {
        return str.replaceAll("^[-+]", "").trim();
    }

    static public Pattern getTokenPattern(String str) {
        String regex;
        regex = str.replaceAll("\\[(.*?)\\]",
                Matcher.quoteReplacement("\\[") +
                "$1" + Matcher.quoteReplacement("\\]"));
        regex = regex.replaceAll("\\-", Matcher.quoteReplacement("\\-"));
        regex = regex.replaceAll("\\*", "([^/]*)");
        regex = regex.replaceAll("([^\\.])\\.([^\\.])", "$1\\\\.$2");
        regex = regex.replaceAll("\\.\\.\\.", "(.*)");
        regex = regex.replaceAll("%%[0-9]", "([^/]*)");
        regex = regex.replaceAll("\\$", Matcher.quoteReplacement("\\$"));
        Pattern pattern = Pattern.compile(regex);
        return pattern;
    }

    static public String doMapping(String lhs, String rhs, String orig) {
        lhs = trimPlusMinus(lhs);
        rhs = trimPlusMinus(rhs);

        Pattern pattern = getTokenPattern(lhs);
        //getting the tokens
        Matcher oldTokens = pattern.matcher(lhs);
        oldTokens.matches();
        Matcher values = pattern.matcher(orig);
        values.matches();
        if(oldTokens.groupCount() != values.groupCount()) {
            return null;
        }
        Map<Integer,String> numberedTokenMap = new HashMap<Integer,String>();
        List<String> tripleDotTokens = new ArrayList<String>();
        List<String> asteriskTokens = new ArrayList<String>();
        //saving values
        for(int i = 1; i<=oldTokens.groupCount(); i++){
            if(oldTokens.group(i).equals("...")) {
                tripleDotTokens.add(values.group(i));
            } else if(oldTokens.group(i).equals("*")) {
                asteriskTokens.add(values.group(i));
            } else if(oldTokens.group(i).startsWith("%%")) {
                numberedTokenMap.put(Integer.valueOf(oldTokens.group(i).substring(2)), values.group(i));
            }
        }

        Iterator<String> tripleDotIterator = tripleDotTokens.iterator();
        Iterator<String> asteriskIterator = asteriskTokens.iterator();
        Map<Integer,String> newGroupMap = new HashMap<Integer,String>();
        String mappedPath = rhs;
        while(true){
            Matcher match = Pattern.compile("\\.\\.\\.").matcher(mappedPath);
            if(match.find()){
                mappedPath = match.replaceFirst( Matcher.quoteReplacement(tripleDotIterator.next()) );
                continue;
            }
            match = Pattern.compile("\\*").matcher(mappedPath);
            if(match.find()){
                mappedPath = match.replaceFirst( Matcher.quoteReplacement(asteriskIterator.next()) );
                continue;
            }
            match = Pattern.compile("%%([0-9])").matcher(mappedPath);
            if(match.find()){
                mappedPath = match.replaceFirst( Matcher.quoteReplacement(numberedTokenMap.get(Integer.valueOf(match.group(1)))) );
                continue;
            }
            break;
        }
        return mappedPath;
    }

}
