package org.infinispan.server.test.security.rest;

import static org.infinispan.server.test.client.rest.RESTHelper.KEY_A;
import static org.infinispan.server.test.client.rest.RESTHelper.KEY_B;
import static org.infinispan.server.test.client.rest.RESTHelper.KEY_C;
import static org.infinispan.server.test.client.rest.RESTHelper.delete;
import static org.infinispan.server.test.client.rest.RESTHelper.fullPathKey;
import static org.infinispan.server.test.client.rest.RESTHelper.get;
import static org.infinispan.server.test.client.rest.RESTHelper.head;
import static org.infinispan.server.test.client.rest.RESTHelper.post;
import static org.infinispan.server.test.client.rest.RESTHelper.put;

import javax.servlet.http.HttpServletResponse;

import org.infinispan.server.test.client.rest.RESTHelper;

/**
 * @author <a href="mailto:vchepeli@redhat.com">Vitalii Chepeliuk</a>
 * @since 7.0
 *
 */
public abstract class AbstractBasicSecurity {
    private static final String TEST_CACHE = "default";
    private static final String TEST_USER_NAME = "testuser";
    //password encoded as is stored in application-users.properties on the server
    private static final String TEST_USER_PASSWORD = "testpassword";
    private static final String KEY_D = "d";

    protected void securedReadWriteOperations() throws Exception {
        RESTHelper.setCredentials(TEST_USER_NAME, TEST_USER_PASSWORD);
        put(fullPathKey(TEST_CACHE, KEY_A), "data", "application/text", HttpServletResponse.SC_OK);
        RESTHelper.clearCredentials();
        put(fullPathKey(TEST_CACHE, KEY_B), "data", "application/text", HttpServletResponse.SC_UNAUTHORIZED);
        RESTHelper.setCredentials(TEST_USER_NAME, TEST_USER_PASSWORD);
        post(fullPathKey(TEST_CACHE, KEY_C), "data", "application/text", HttpServletResponse.SC_OK);
        RESTHelper.clearCredentials();
        post(fullPathKey(TEST_CACHE, KEY_D), "data", "application/text", HttpServletResponse.SC_UNAUTHORIZED);
        get(fullPathKey(TEST_CACHE, KEY_A), HttpServletResponse.SC_UNAUTHORIZED);
        RESTHelper.setCredentials(TEST_USER_NAME, TEST_USER_PASSWORD);
        get(fullPathKey(TEST_CACHE, KEY_A), "data");
        RESTHelper.clearCredentials();
        head(fullPathKey(TEST_CACHE, KEY_A), HttpServletResponse.SC_UNAUTHORIZED);
        RESTHelper.setCredentials(TEST_USER_NAME, TEST_USER_PASSWORD);
        head(fullPathKey(TEST_CACHE, KEY_A), HttpServletResponse.SC_OK);
        RESTHelper.clearCredentials();
        delete(fullPathKey(TEST_CACHE, KEY_A), HttpServletResponse.SC_UNAUTHORIZED);
        RESTHelper.setCredentials(TEST_USER_NAME, TEST_USER_PASSWORD);
        delete(fullPathKey(TEST_CACHE, KEY_A), HttpServletResponse.SC_OK);
        delete(fullPathKey(TEST_CACHE, KEY_C), HttpServletResponse.SC_OK);
        RESTHelper.clearCredentials();
    }
}
