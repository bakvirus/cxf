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

package org.apache.cxf.systest.jaxrs.security.xml;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.cxf.Bus;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.apache.cxf.jaxrs.client.ClientWebApplicationException;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.apache.cxf.jaxrs.client.ServerWebApplicationException;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.rs.security.common.SecurityUtils;
import org.apache.cxf.rs.security.xml.XmlEncInInterceptor;
import org.apache.cxf.rs.security.xml.XmlEncOutInterceptor;
import org.apache.cxf.rs.security.xml.XmlSigInInterceptor;
import org.apache.cxf.rs.security.xml.XmlSigOutInterceptor;
import org.apache.cxf.systest.jaxrs.security.Book;
import org.apache.cxf.systest.jaxrs.security.BookStore;
import org.apache.cxf.testutil.common.AbstractBusClientServerTestBase;
import org.apache.xml.security.encryption.XMLCipher;

import org.junit.BeforeClass;
import org.junit.Test;

public class JAXRSXmlSecTest extends AbstractBusClientServerTestBase {
    public static final String PORT = BookServerXmlSec.PORT;

    @BeforeClass
    public static void startServers() throws Exception {
        assertTrue("server did not launch correctly", 
                   launchServer(BookServerXmlSec.class, true));
    }
    
    @Test
    public void testPostBookWithEnvelopedSigAndProxy() throws Exception {
        String address = "https://localhost:" + PORT + "/xmlsig";
        doTestSignatureProxy(address, false);
    }
    
    private void doTestSignatureProxy(String address, boolean enveloping) {
        JAXRSClientFactoryBean bean = new JAXRSClientFactoryBean();
        bean.setAddress(address);
        
        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = JAXRSXmlSecTest.class.getResource("client.xml");
        Bus springBus = bf.createBus(busFile.toString());
        bean.setBus(springBus);

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("ws-security.callback-handler", 
                       "org.apache.cxf.systest.jaxrs.security.saml.KeystorePasswordCallback");
        properties.put("ws-security.signature.username", "alice");
        properties.put("ws-security.signature.properties", 
                       "org/apache/cxf/systest/jaxrs/security/alice.properties");
        bean.setProperties(properties);
        XmlSigOutInterceptor sigInterceptor = new XmlSigOutInterceptor();
        if (enveloping) {
            sigInterceptor.setStyle(XmlSigOutInterceptor.ENVELOPING_SIG);
        }
        bean.getOutInterceptors().add(sigInterceptor);
        bean.setServiceClass(BookStore.class);
        
        BookStore store = bean.create(BookStore.class);
        try {
            Book book = store.addBook(new Book("CXF", 126L));
            assertEquals(126L, book.getId());
        } catch (ServerWebApplicationException ex) {
            fail(ex.getMessage());
        } catch (ClientWebApplicationException ex) {
            if (ex.getCause() != null && ex.getCause().getMessage() != null) {
                fail(ex.getCause().getMessage());
            } else {
                fail(ex.getMessage());
            }
        }
    }
    
    @Test
    public void testPostBookWithEnvelopedSig() throws Exception {
        String address = "https://localhost:" + PORT + "/xmlsig/bookstore/books";
        doTestSignature(address, false);
    }
    
    @Test
    public void testPostBookWithEnvelopingSig() throws Exception {
        String address = "https://localhost:" + PORT + "/xmlsig/bookstore/books";
        doTestSignature(address, true);
    }
    
    private void doTestSignature(String address, boolean enveloping) {
        JAXRSClientFactoryBean bean = new JAXRSClientFactoryBean();
        bean.setAddress(address);
        
        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = JAXRSXmlSecTest.class.getResource("client.xml");
        Bus springBus = bf.createBus(busFile.toString());
        bean.setBus(springBus);

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("ws-security.callback-handler", 
                       "org.apache.cxf.systest.jaxrs.security.saml.KeystorePasswordCallback");
        properties.put("ws-security.signature.username", "alice");
        properties.put("ws-security.signature.properties", 
                       "org/apache/cxf/systest/jaxrs/security/alice.properties");
        bean.setProperties(properties);
        XmlSigOutInterceptor sigInterceptor = new XmlSigOutInterceptor();
        if (enveloping) {
            sigInterceptor.setStyle(XmlSigOutInterceptor.ENVELOPING_SIG);
        }
        bean.getOutInterceptors().add(sigInterceptor);
        bean.getInInterceptors().add(new XmlSigInInterceptor());
        
        WebClient wc = bean.createWebClient();
        try {
            Book book = wc.post(new Book("CXF", 126L), Book.class);
            assertEquals(126L, book.getId());
        } catch (ServerWebApplicationException ex) {
            fail(ex.getMessage());
        } catch (ClientWebApplicationException ex) {
            if (ex.getCause() != null && ex.getCause().getMessage() != null) {
                fail(ex.getCause().getMessage());
            } else {
                fail(ex.getMessage());
            }
        }
    }
    
    @Test
    public void testPostEncryptedBook() throws Exception {
        String address = "https://localhost:" + PORT + "/xmlenc/bookstore/books";
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("ws-security.callback-handler", 
                       "org.apache.cxf.systest.jaxrs.security.saml.KeystorePasswordCallback");
        properties.put("ws-security.encryption.username", "bob");
        properties.put("ws-security.encryption.properties", 
                       "org/apache/cxf/systest/jaxrs/security/bob.properties");
        doTestPostEncryptedBook(address, properties);
    }
    
    @Test
    public void testPostEncryptedBookIssuerSerial() throws Exception {
        String address = "https://localhost:" + PORT + "/xmlenc/bookstore/books";
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("ws-security.callback-handler", 
                       "org.apache.cxf.systest.jaxrs.security.saml.KeystorePasswordCallback");
        properties.put("ws-security.encryption.username", "bob");
        properties.put("ws-security.encryption.properties", 
                       "org/apache/cxf/systest/jaxrs/security/bob.properties");
        doTestPostEncryptedBook(address, properties, SecurityUtils.X509_ISSUER_SERIAL);
    }
    
    @Test
    public void testPostEncryptedSignedBook() throws Exception {
        String address = "https://localhost:" + PORT + "/xmlsec/bookstore/books";
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("ws-security.callback-handler", 
                       "org.apache.cxf.systest.jaxrs.security.saml.KeystorePasswordCallback");
        properties.put("ws-security.encryption.username", "bob");
        properties.put("ws-security.encryption.properties", 
                       "org/apache/cxf/systest/jaxrs/security/bob.properties");
        properties.put("ws-security.signature.username", "alice");
        properties.put("ws-security.signature.properties", 
                       "org/apache/cxf/systest/jaxrs/security/alice.properties");
        doTestPostEncryptedBook(address, properties);
        
    }
    
    @Test
    //Encryption properties are shared by encryption and signature handlers
    public void testPostEncryptedSignedBookSharedProps() throws Exception {
        String address = "https://localhost:" + PORT + "/xmlsec2/bookstore/books";
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("ws-security.callback-handler", 
                       "org.apache.cxf.systest.jaxrs.security.saml.KeystorePasswordCallback");
        properties.put("ws-security.encryption.username", "bob");
        properties.put("ws-security.encryption.properties", 
                       "org/apache/cxf/systest/jaxrs/security/bob.properties");
        doTestPostEncryptedBook(address, properties);
        
    }
    
    public void doTestPostEncryptedBook(String address, Map<String, Object> properties) 
        throws Exception {
        doTestPostEncryptedBook(address, properties, SecurityUtils.X509_KEY);
    }
    
    public void doTestPostEncryptedBook(String address, Map<String, Object> properties,
                                        String keyIdentifierType) 
        throws Exception {
        JAXRSClientFactoryBean bean = new JAXRSClientFactoryBean();
        bean.setAddress(address);
        
        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = JAXRSXmlSecTest.class.getResource("client.xml");
        Bus springBus = bf.createBus(busFile.toString());
        bean.setBus(springBus);

        bean.setProperties(properties);
        bean.getOutInterceptors().add(new XmlSigOutInterceptor());
        XmlEncOutInterceptor encInterceptor = new XmlEncOutInterceptor();
        encInterceptor.setKeyIdentifierType(keyIdentifierType);
        encInterceptor.setSymmetricEncAlgorithm(XMLCipher.AES_128);
        bean.getOutInterceptors().add(encInterceptor);
        
        bean.getInInterceptors().add(new XmlEncInInterceptor());
        
        WebClient wc = bean.createWebClient();
        try {
            Book book = wc.post(new Book("CXF", 126L), Book.class);
            assertEquals(126L, book.getId());
        } catch (ServerWebApplicationException ex) {
            fail(ex.getMessage());
        } catch (ClientWebApplicationException ex) {
            if (ex.getCause() != null && ex.getCause().getMessage() != null) {
                fail(ex.getCause().getMessage());
            } else {
                fail(ex.getMessage());
            }
        }
        
    }
    
    
}
