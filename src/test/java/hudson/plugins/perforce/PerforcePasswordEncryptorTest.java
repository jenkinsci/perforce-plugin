/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package hudson.plugins.perforce;

import org.jvnet.hudson.test.HudsonTestCase;

/**
 *
 * @author stimmslocal
 */
public class PerforcePasswordEncryptorTest extends HudsonTestCase {


    public void testEncryptionRoundTrip()
    {
        PerforcePasswordEncryptor encryptor = new PerforcePasswordEncryptor();
        String toEncrypt = "Some random text!";
        
        String encryptedString = encryptor.encryptString(toEncrypt);
        assertNotSame(toEncrypt, encryptedString);
        String decryptedString = encryptor.decryptString(encryptedString);
        assertEquals(toEncrypt, decryptedString);

    }

}
