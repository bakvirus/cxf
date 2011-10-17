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
package org.apache.cxf.systest.sts.intermediary_transformation;

import java.net.URL;

import javax.xml.namespace.QName;
import javax.xml.ws.Service;

import org.apache.cxf.Bus;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.apache.cxf.systest.sts.deployment.STSServer;
import org.apache.cxf.testutil.common.AbstractBusClientServerTestBase;
import org.example.contract.doubleit.DoubleItPortType;
import org.junit.BeforeClass;

/**
 * In this test case, a CXF client sends a Username Token via (1-way) TLS to a STS instance, and
 * receives a (HOK) SAML 1.1 Assertion. This is then sent via (1-way) TLS to an Intermediary 
 * service provider. The intermediary service provider validates the token, and then the 
 * Intermediary client uses delegation to dispatch the received token (via OnBehalfOf) to another 
 * STS instance. After this point, the STSClient is disabled, meaning that the Intermediary client must rely
 * on its cache to get tokens. The retrieved token is sent to the service provider via (2-way) TLS.
 */
public class IntermediaryTransformationCachingTest extends AbstractBusClientServerTestBase {
    
    static final String PORT2 = allocatePort(Server.class, 2);
    
    private static final String NAMESPACE = "http://www.example.org/contract/DoubleIt";
    private static final QName SERVICE_QNAME = new QName(NAMESPACE, "DoubleItService");
    
    private static final String PORT = allocatePort(Intermediary.class);

    @BeforeClass
    public static void startServers() throws Exception {
        assertTrue(
            "Intermediary failed to launch",
            // run the Intermediary in the same process
            // set this to false to fork
            launchServer(IntermediaryCaching.class, true)
        );
        assertTrue(
            "Server failed to launch",
            // run the server in the same process
            // set this to false to fork
            launchServer(Server.class, true)
        );
        String deployment = System.getProperty("sts.deployment");
        if ("standalone".equals(deployment)) {
            assertTrue(
                    "Server failed to launch",
                    // run the server in the same process
                    // set this to false to fork
                    launchServer(STSServer.class, true)
            );
        }
    }

    @org.junit.Test
    public void testIntermediaryTransformationCaching() throws Exception {

        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = IntermediaryTransformationCachingTest.class.getResource("cxf-client.xml");

        Bus bus = bf.createBus(busFile.toString());
        SpringBusFactory.setDefaultBus(bus);
        SpringBusFactory.setThreadDefaultBus(bus);
        
        URL wsdl = IntermediaryTransformationCachingTest.class.getResource("DoubleIt.wsdl");
        Service service = Service.create(wsdl, SERVICE_QNAME);
        QName portQName = new QName(NAMESPACE, "DoubleItTransportSAML1EndorsingPort");
        DoubleItPortType transportPort = 
            service.getPort(portQName, DoubleItPortType.class);
        updateAddressPort(transportPort, PORT);

        // Make initial successful invocation
        doubleIt(transportPort, 25);
        
        // Make another invocation - this should work as the intermediary caches the token
        // even though its STSClient is disabled after the first invocation
        doubleIt(transportPort, 30);
    }
    
    private static void doubleIt(DoubleItPortType port, int numToDouble) {
        int resp = port.doubleIt(numToDouble);
        System.out.println("The number " + numToDouble + " doubled is " + resp);
        assertTrue(resp == 2 * numToDouble);
    }
}
