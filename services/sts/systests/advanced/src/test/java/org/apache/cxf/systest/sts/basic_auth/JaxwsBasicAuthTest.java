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
package org.apache.cxf.systest.sts.basic_auth;

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
 * In this test case, a CXF JAX-WS client sends BasicAuth via (1-way) TLS to a CXF provider.
 * The provider converts it into Username Token and dispatches it to an STS for validation 
 * (via TLS). 
 */
public class JaxwsBasicAuthTest extends AbstractBusClientServerTestBase {
    
    static final String STSPORT = allocatePort(STSServer.class);
    
    private static final String NAMESPACE = "http://www.example.org/contract/DoubleIt";
    private static final QName SERVICE_QNAME = new QName(NAMESPACE, "DoubleItService");
    
    private static final String PORT = allocatePort(Server.class);

    @BeforeClass
    public static void startServers() throws Exception {
        assertTrue(
                   "Server failed to launch",
                   // run the server in the same process
                   // set this to false to fork
                   launchServer(Server.class, true)
        );
        assertTrue(
                   "Server failed to launch",
                   // run the server in the same process
                   // set this to false to fork
                   launchServer(STSServer.class, true)
        );
    }

    @org.junit.Test
    public void testBasicAuth() throws Exception {

        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = JaxwsBasicAuthTest.class.getResource("cxf-client.xml");

        Bus bus = bf.createBus(busFile.toString());
        SpringBusFactory.setDefaultBus(bus);
        SpringBusFactory.setThreadDefaultBus(bus);

        URL wsdl = JaxwsBasicAuthTest.class.getResource("DoubleIt.wsdl");
        Service service = Service.create(wsdl, SERVICE_QNAME);
        QName portQName = new QName(NAMESPACE, "DoubleItPort");
        DoubleItPortType port = 
            service.getPort(portQName, DoubleItPortType.class);
        updateAddressPort(port, PORT);
        
        doubleIt(port, 25);
    }
    
    @org.junit.Test
    public void testBadBasicAuth() throws Exception {

        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = JaxwsBasicAuthTest.class.getResource("cxf-bad-client.xml");

        Bus bus = bf.createBus(busFile.toString());
        SpringBusFactory.setDefaultBus(bus);
        SpringBusFactory.setThreadDefaultBus(bus);

        URL wsdl = JaxwsBasicAuthTest.class.getResource("DoubleIt.wsdl");
        Service service = Service.create(wsdl, SERVICE_QNAME);
        QName portQName = new QName(NAMESPACE, "DoubleItPort");
        DoubleItPortType port = 
            service.getPort(portQName, DoubleItPortType.class);
        updateAddressPort(port, PORT);

        try {
            doubleIt(port, 30);
            fail("Expected failure on a bad password");
        } catch (javax.xml.ws.soap.SOAPFaultException fault) {
            String message = fault.getMessage();
            assertTrue(message.contains("STS Authentication failed")
                || message.contains("Validation of security token failed"));
        }
    }

    private static void doubleIt(DoubleItPortType port, int numToDouble) {
        int resp = port.doubleIt(numToDouble);
        assertEquals(numToDouble * 2 , resp);
    }
}
