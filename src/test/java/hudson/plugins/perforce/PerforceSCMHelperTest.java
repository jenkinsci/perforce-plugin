package hudson.plugins.perforce;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

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

}
