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

package org.apache.cxf.jaxrs.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.cxf.Bus;
import org.apache.cxf.feature.AbstractFeature;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.InterceptorProvider;
import org.apache.cxf.jaxrs.model.UserOperation;
import org.apache.cxf.jaxrs.model.UserResource;
import org.apache.cxf.jaxrs.resources.Book;
import org.apache.cxf.jaxrs.resources.BookStore;
import org.apache.cxf.jaxrs.resources.BookStoreSubresourcesOnly;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.http.HTTPConduit;

import org.junit.Assert;
import org.junit.Test;

public class JAXRSClientFactoryBeanTest extends Assert {

    @Test
    public void testCreateClient() throws Exception {
        JAXRSClientFactoryBean bean = new JAXRSClientFactoryBean();
        bean.setAddress("http://bar");
        bean.setResourceClass(BookStore.class);
        assertTrue(bean.create() instanceof BookStore);
    }
    
    @Test
    public void testCreateClientWithUserResource() throws Exception {
        JAXRSClientFactoryBean bean = new JAXRSClientFactoryBean();
        bean.setAddress("http://bar");
        UserResource r = new UserResource();
        r.setName(BookStore.class.getName());
        r.setPath("/");
        UserOperation op = new UserOperation();
        op.setName("getDescription");
        op.setVerb("GET");
        r.setOperations(Collections.singletonList(op));
        bean.setModelBeans(r);
        assertTrue(bean.create() instanceof BookStore);
    }
    
    @Test
    public void testCreateClientWithTwoUserResources() throws Exception {
        JAXRSClientFactoryBean bean = new JAXRSClientFactoryBean();
        bean.setAddress("http://bar");
        UserResource r1 = new UserResource();
        r1.setName(BookStore.class.getName());
        r1.setPath("/store");
        UserOperation op = new UserOperation();
        op.setName("getDescription");
        op.setVerb("GET");
        r1.setOperations(Collections.singletonList(op));
        
        UserResource r2 = new UserResource();
        r2.setName(Book.class.getName());
        r2.setPath("/book");
        UserOperation op2 = new UserOperation();
        op2.setName("getName");
        op2.setVerb("GET");
        r2.setOperations(Collections.singletonList(op2));
        
        bean.setModelBeans(r1, r2);
        bean.setServiceClass(Book.class);
        assertTrue(bean.create() instanceof Book);
    }
    
    @Test
    public void testGetConduit() throws Exception {
        JAXRSClientFactoryBean bean = new JAXRSClientFactoryBean();
        bean.setAddress("http://bar");
        bean.setResourceClass(BookStore.class);
        BookStore store = bean.create(BookStore.class);
        Conduit conduit = WebClient.getConfig(store).getConduit();
        assertTrue(conduit instanceof HTTPConduit);
    }
    
    @Test
    public void testTemplateInRootPathInherit() throws Exception {
        JAXRSClientFactoryBean bean = new JAXRSClientFactoryBean();
        bean.setAddress("http://bar");
        bean.setResourceClass(BookStoreSubresourcesOnly.class);
        BookStoreSubresourcesOnly store = bean.create(BookStoreSubresourcesOnly.class, 1, 2, 3);
        BookStoreSubresourcesOnly store2 = store.getItself();
        assertEquals("http://bar/bookstore/1/2/3/sub1", 
                     WebClient.client(store2).getCurrentURI().toString());
    }
    
    @Test
    public void testTemplateInRootReplace() throws Exception {
        JAXRSClientFactoryBean bean = new JAXRSClientFactoryBean();
        bean.setAddress("http://bar");
        bean.setResourceClass(BookStoreSubresourcesOnly.class);
        BookStoreSubresourcesOnly store = bean.create(BookStoreSubresourcesOnly.class, 1, 2, 3);
        BookStoreSubresourcesOnly store2 = store.getItself2("11", "33");
        assertEquals("http://bar/bookstore/11/2/33/sub2", 
                     WebClient.client(store2).getCurrentURI().toString());
    }
    
    @Test
    public void testTemplateInRootAppend() throws Exception {
        JAXRSClientFactoryBean bean = new JAXRSClientFactoryBean();
        bean.setAddress("http://bar");
        bean.setResourceClass(BookStoreSubresourcesOnly.class);
        BookStoreSubresourcesOnly store = bean.create(BookStoreSubresourcesOnly.class, 1, 2, 3);
        BookStoreSubresourcesOnly store2 = store.getItself3("id4");
        assertEquals("http://bar/bookstore/1/2/3/id4/sub3", 
                     WebClient.client(store2).getCurrentURI().toString());
    }
    
    @Test
    public void testAddLoggingToClient() throws Exception {
        JAXRSClientFactoryBean bean = new JAXRSClientFactoryBean();
        bean.setAddress("http://bar");
        bean.setResourceClass(BookStoreSubresourcesOnly.class);
        TestFeature testFeature = new TestFeature();
        List<AbstractFeature> features = new ArrayList<AbstractFeature>();
        features.add(testFeature);
        bean.setFeatures(features);
        
        BookStoreSubresourcesOnly store = bean.create(BookStoreSubresourcesOnly.class, 1, 2, 3);
        assertTrue("TestFeature wasn't initialized", testFeature.isInitialized());
        BookStoreSubresourcesOnly store2 = store.getItself3("id4");
        assertEquals("http://bar/bookstore/1/2/3/id4/sub3", 
                     WebClient.client(store2).getCurrentURI().toString());
    }
    
    
    private class TestFeature extends AbstractFeature {
        private TestInterceptor testInterceptor;

        @Override
        protected void initializeProvider(InterceptorProvider provider, Bus bus) {
            testInterceptor = new TestInterceptor();
            provider.getOutInterceptors().add(testInterceptor);
        }

        protected boolean isInitialized() {
            return testInterceptor.isInitialized();
        }
    }
 
    private class TestInterceptor extends AbstractPhaseInterceptor<Message> {
        private boolean isInitialized;

        public TestInterceptor() {
            this(Phase.PRE_STREAM);
        }

        public TestInterceptor(String s) {
            super(Phase.PRE_STREAM);
            isInitialized = true;
        } 

        public void handleMessage(Message message) throws Fault {
        }

        protected boolean isInitialized() {
            return isInitialized;
        }

    }
    
}
