/*
 * The MIT License
 *
 * Copyright 2013 Oleg Nenashev <nenashev@synopsys.com>, Synopsys Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.synopsys.arc.jenkinsci.plugins.perforce;

import hudson.EnvVars;
import hudson.plugins.perforce.utils.MacroStringHelper;
import hudson.plugins.perforce.utils.ParameterSubstitutionException;
import javax.annotation.Nonnull;
import junit.framework.Assert;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;

/**
 *
 * @author Oleg Nenashev <nenashev@synopsys.com>, Synopsys Inc.
 * @see MacroStringHelper
 */
public class MacroStringHelperTest {
    
    
    @Test
    public void Sanity_CorrectStrings() {
        checkCorrectString("test");
        checkCorrectString("test/with/multiple/path/levels");
        checkCorrectString("test/with/multiple/path/levels/and/points...");
        checkCorrectString("test/multistring");
    }
    
    @Test
    public void Sanity_IncorrectStrings() {
        checkInorrectString("${SINGLE_VAR}");
        checkInorrectString("${MULTIPLE_VARS} ${VAR2}");
        checkInorrectString("${MULTIPLE_VARS} ${VAR2}");
        checkInorrectString("text${VAR}");
        checkInorrectString("${VAR}text");
        checkInorrectString("/${VAR_IN_THE_PATH}/some/items");
    }
    
    @Test
    public void Sanity_CheckNull() {
        checkCorrectString(null);
    }
    
    @Bug(25365)
    public @Test void checkMultiLineString() {
        checkStringForMacros("${param1}/...\n${param2}/...", true);
        checkStringForMacros("$vdvdvd/...\n\t${param2}/...", true);
        checkStringForMacros("${param1}/...\nJust a stub string", true);
        
        // no macros
        checkStringForMacros("//depot1/path1/... //placeholder/path1/...\r\n\t//depot1/path2/... //placeholder/path2/...", false);
        checkStringForMacros("//depot1/path1/... //placeholder/path1/...\n//depot1/path2/... //placeholder/path2/...", false);
    }
    
    @Bug(25732)
    public @Test void nullEntriesInEnvVars() throws ParameterSubstitutionException {   
        // Check that params with null values are being ignored
        try {
           MacroStringHelper.substituteParameters("Test sring with ${PARAM}", new EnvVars("PARAM", null));
        } catch (ParameterSubstitutionException ex) {
            return; // OK
        }
        Assert.fail("Expected ParameterSubstitutionException on null parameter value");
    }
    
    public static void checkStringForMacros(@Nonnull String string, boolean expectMacro) {
        boolean isMacro = MacroStringHelper.containsMacro(string);
        if (isMacro != expectMacro) {
            if (expectMacro) {
                Assert.fail("Macros have not been found in '" + string + "'");
            } else {
                Assert.fail("Wrong macro discodvered in '" + string + "'");
            }
        }
    }
    
    public static void checkTest(String string, boolean expectError) {
        try {
            MacroStringHelper.checkString(string);
        } catch (ParameterSubstitutionException ex) {
            System.out.println("String check error: " + ex);
            ex.printStackTrace();
            if (!expectError) {
                Assert.fail(ex.getMessage());
            }
            return;
        }
        
        if (expectError) {
            Assert.fail("Expected ParameterSubstitutionException, but stringCheck has finished successfully");
        }
    }
    
    public static void checkCorrectString(String str) {
        System.out.println("Checking correct string '"+str+"' ...");
        checkTest(str, false);
    }
    
    public static void checkInorrectString(String str) {
        System.out.println("Checking incorrect string '"+str+"' ...");
        checkTest(str, true);
    }  
}
