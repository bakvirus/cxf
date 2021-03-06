/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.sts.cache;

import org.apache.cxf.ws.security.tokenstore.SecurityToken;
import org.junit.BeforeClass;

public class HazelCastTokenStoreTest extends org.junit.Assert {
  
    private static STSTokenStore store;
    
    @BeforeClass
    public static void init() throws Exception {
        store = new HazelCastTokenStore("default");
    }
    
    // tests STSCache apis for storing in the cache.
    @org.junit.Test
    public void testCacheStore() throws Exception {
        String key = "key";
        SecurityToken token = new SecurityToken(key);
        store.add(token);
        SecurityToken cachedToken = store.getToken(key);
        assertEquals(token.getId(), cachedToken.getId());
        store.remove(token);
        assertNull(store.getToken(key));
        store.add(token, new Integer(1));
        assertNotNull(store.getToken(key));
        Thread.sleep(2000);
        assertNull(store.getToken(key));
    }
    
    // tests STSCache apis for removing from the cache.
    @org.junit.Test
    public void testCacheRemove() {
        SecurityToken token1 = new SecurityToken("token1");
        SecurityToken token2 = new SecurityToken("token2");
        SecurityToken token3 = new SecurityToken("token3");
        store.add(token1);
        store.add(token2);
        store.add(token3);
        assertTrue(store.getValidTokens().size() == 3);
        store.remove(token3);
        assertNull(store.getToken("test3"));
        store.remove(token1);
        store.remove(token2);
        assertTrue(store.getValidTokens().size() == 0);
    }
}
