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

import hudson.plugins.perforce.utils.MacroStringHelper;
import hudson.plugins.perforce.utils.ParameterSubstitutionException;
import junit.framework.Assert;
import org.junit.Test;

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
