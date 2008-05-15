package hudson.plugins.perforce;

import java.util.List;
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
	 *
	 * @return
	 */
	static String computePathFromViews(List<String> views) {

		StringBuilder path = new StringBuilder("");

		for(String view : views) {
			StringTokenizer columns = new StringTokenizer(view, " ");
			String leftColumn = columns.nextToken().trim();
			if(leftColumn.indexOf(EXCLUSION_VIEW_PREFIX + DEPOT_ROOT) != -1) {
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
		if(
				projectPath.equals("//...") // root of depot ok
						|| projectPath.indexOf('@') > -1) // labels ok
		{
			return true;
		}
		return false;
	}


}
