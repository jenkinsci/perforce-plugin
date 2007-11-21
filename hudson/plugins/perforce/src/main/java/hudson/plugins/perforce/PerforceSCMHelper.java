/**
 * 
 */
package hudson.plugins.perforce;

import java.util.List;
import java.util.StringTokenizer;


/**
 * @author Brian Westrich
 *
 */
public final class PerforceSCMHelper {

	private static final String DEPOT_ROOT = "//";
	private static final String EXCLUSION_VIEW_PREFIX = "-";

	private PerforceSCMHelper() {
		// static methods, do not instantiate 
	}

	/**
	 * Generate a path for the changes command based on a workspaces views.
	 * @param views
	 * @return
	 */
	static String computePathFromViews(List<String> views) {
		
		StringBuilder path = new StringBuilder("");
		
		for(String view : views) {
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
	
	

}
