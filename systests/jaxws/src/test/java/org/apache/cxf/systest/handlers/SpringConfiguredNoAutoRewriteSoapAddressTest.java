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

package org.apache.cxf.systest.handlers;

import java.util.List;

import org.apache.cxf.testutil.common.TestUtil;
import org.apache.handlers.AddNumbers;
import org.junit.Test;

/**
 *
 */
public class SpringConfiguredNoAutoRewriteSoapAddressTest
        extends AbstractSpringConfiguredAutoRewriteSoapAddressTest {
    static String port = TestUtil.getPortNumber("springportNoAutoRewriteSoapAddressTest");

    @Override
    protected String[] getConfigLocations() {
        return new String[]
        {"classpath:/org/apache/cxf/systest/handlers/beans_no_autoRewriteSoapAddress.xml" };
    }

    @Test
    public void testWsdlAddress() throws Exception {
        System.err.println("portNumber: " + port);
        System.err.println(getApplicationContext().getBean("cxfHandlerTestEndpoint").
                                                                   getClass().toString());

        AddNumbers addNumbers = getApplicationContext().getBean("cxfHandlerTestClientEndpoint",
                                                                   AddNumbers.class);

        int r = addNumbers.addNumbers(10, 15);
        assertEquals(1015, r);


        List<String> serviceUrls = findAllServiceUrlsFromWsdl("localhost", port);
        assertEquals(1, serviceUrls.size());
        assertEquals("http://localhost:" + port + "/SpringEndpoint", serviceUrls.get(0));

        serviceUrls = findAllServiceUrlsFromWsdl("127.0.0.1", port);
        assertEquals(1, serviceUrls.size());
        assertEquals("http://localhost:" + port + "/SpringEndpoint", serviceUrls.get(0));

    }

}
