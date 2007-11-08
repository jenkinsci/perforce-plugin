package com.tek42.perforce.parse;

import java.util.List;

import junit.framework.TestCase;

import com.tek42.perforce.Depot;
import com.tek42.perforce.PerforceException;

/*
 * some basic, non-reproducible unit tests, to help learn the p4java library. 
 */
public class ChangesTest extends TestCase {

	private static final String WORKSPACE = "Brian-mcwest5";
	private static final String PATH = "//testfw/...";
	private Depot depot;
	
	boolean depotIsValid = true; 

// These tests are commented out since they assume presence of a local perforce server. 	
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		depot = new Depot();
		depot.setPort("localhost:1666");
		depot.setUser("Brian");
		try { 
		depotIsValid = depot.getStatus().isValid();
		} catch (Exception e) { 
			depotIsValid = false;
		}
	}

	public void testGetChangeNumbersTo() throws PerforceException {
		if (!depotIsValid) { 
			return; 
		}
		
		List<Integer> numbers = depot.getChanges()
			.getChangeNumbersTo(WORKSPACE, PATH, 107);
		assert(numbers != null);
	}
	
	public void testGetChangeNumbers() throws PerforceException { 
		if (!depotIsValid) { 
			return; 
		}
		
		List<Integer> changes = depot.getChanges()
			.getChangeNumbers(PATH, -1, 10);
		assert(changes != null);
	}
	
	public void testGetChangeNumbersToWithMoreThanThreePaths() throws PerforceException {
		if (!depotIsValid) { 
			return; 
		}
		
		List<Integer> numbers = depot.getChanges()
			.getChangeNumbersTo("//testfw/... //testproj/... " +
					"//testfw/... //testfw/src/...", 1);
		assert(numbers != null);
		
	}

	
	
	 

}
