package hudson.plugins.perforce;

import javax.annotation.CheckForNull;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * @author stimmslocal
 */
public class PerforcePasswordEncryptorTest extends HudsonTestCase {


    public void testEncryptionRoundTrip() {
        PerforcePasswordEncryptor encryptor = new PerforcePasswordEncryptor();
        String toEncrypt = "Some random text!";

        String encryptedString = encryptor.encryptString(toEncrypt);
        assertNotSame(toEncrypt, encryptedString);
        String decryptedString = encryptor.decryptString(encryptedString);
        assertEquals(toEncrypt, decryptedString);
    }

    public void testAppearsToBeEncyptedFindsEncryptedString() {
        PerforcePasswordEncryptor encryptor = new PerforcePasswordEncryptor();
        String toEncrypt = "Some random text!";

        String encryptedString = encryptor.encryptString(toEncrypt);
        assertTrue(encryptor.appearsToBeAnEncryptedPassword(encryptedString));
    }
    
    @Bug(26076)
    public void testEncryptNull() {
        final PerforcePasswordEncryptor encryptor = new PerforcePasswordEncryptor();
        final @CheckForNull String toEncrypt = null;

        assertFalse(encryptor.appearsToBeAnEncryptedPassword(null));
        String encryptedString = encryptor.encryptString(toEncrypt);
        assertEquals("", encryptedString);
    }
}
