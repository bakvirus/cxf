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

package org.apache.cxf.phase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.message.Message;
import org.easymock.classextension.EasyMock;
import org.easymock.classextension.IMocksControl;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class PhaseInterceptorChainTest extends Assert {

    private IMocksControl control;

    private PhaseInterceptorChain chain;

    private Message message;

    @Before
    public void setUp() {

        control = EasyMock.createNiceControl();
        message = control.createMock(Message.class);

        Phase phase1 = new Phase("phase1", 1);
        Phase phase2 = new Phase("phase2", 2);
        Phase phase3 = new Phase("phase3", 3);
        List<Phase> phases = new ArrayList<Phase>();
        phases.add(phase1);
        phases.add(phase2);
        phases.add(phase3);

        chain = new PhaseInterceptorChain(phases);
    }

    @After
    public void tearDown() {
        control.verify();
    }

    @Test
    public void testAddOneInterceptor() {
        AbstractPhaseInterceptor p = setUpPhaseInterceptor("phase1", "p1");
        control.replay();
        chain.add(p);
        Iterator<Interceptor<? extends Message>> it = chain.iterator();
        assertSame(p, it.next());
        assertTrue(!it.hasNext());
    }
    
    @Test
    public void testForceAddSameInterceptor() {

        AbstractPhaseInterceptor p = setUpPhaseInterceptor("phase1", "p1");
        control.replay();
        chain.add(p, false);
        chain.add(p, false);
        Iterator<Interceptor<? extends Message>> it = chain.iterator();
        assertSame(p, it.next());
        assertTrue(!it.hasNext());
        chain.add(p, true);
        it = chain.iterator();
        assertSame(p, it.next());
        assertSame(p, it.next());
        assertTrue(!it.hasNext()); 
    }
    
    @Test
    public void testForceAddSameInterceptorType() {

        AbstractPhaseInterceptor p1 = setUpPhaseInterceptor("phase1", "p1");
        AbstractPhaseInterceptor p2 = setUpPhaseInterceptor("phase1", "p1");
        control.replay();
        chain.add(p1, false);
        chain.add(p2, false);
        Iterator<Interceptor<? extends Message>> it = chain.iterator();
        assertSame(p1, it.next());
        assertTrue(!it.hasNext());
        chain.add(p2, true);
        it = chain.iterator();
        assertSame(p2, it.next());
        assertSame(p1, it.next());
        assertTrue(!it.hasNext()); 
    }

    @Test
    public void testAddTwoInterceptorsSamePhase() {
        AbstractPhaseInterceptor p1 = setUpPhaseInterceptor("phase1", "p1");
        Set<String> after = new HashSet<String>();
        after.add("p1");
        AbstractPhaseInterceptor p2 = setUpPhaseInterceptor("phase1", "p2",
                after);
        control.replay();
        chain.add(p1);
        chain.add(p2);
        Iterator<Interceptor<? extends Message>> it = chain.iterator();

        assertSame("Unexpected interceptor at this position.", p1, it.next());
        assertSame("Unexpected interceptor at this position.", p2, it.next());
        assertTrue(!it.hasNext());
    }
    
    AbstractPhaseInterceptor setUpPhaseInterceptor(String phase, String id,
                                                   Set<String> b, boolean be) {
        AbstractPhaseInterceptor p = control
            .createMock(AbstractPhaseInterceptor.class);
        EasyMock.expect(p.getPhase()).andReturn(phase).anyTimes();
        EasyMock.expect(p.getId()).andReturn(id).anyTimes();
        Set<String> after = new HashSet<String>();
        Set<String> before = null == b ? new HashSet<String>() : b;
        EasyMock.expect(p.getBefore()).andReturn(before).anyTimes();
        EasyMock.expect(p.getAfter()).andReturn(after).anyTimes();

        return p;
    }
    
    @Test
    public void testThreeInterceptorSamePhaseWithOrder() {
        AbstractPhaseInterceptor p1 = setUpPhaseInterceptor("phase1", "p1");
        Set<String> before = new HashSet<String>();
        before.add("p1");
        AbstractPhaseInterceptor p2 = setUpPhaseInterceptor("phase1", "p2", before, true);
        Set<String> before1 = new HashSet<String>();
        before1.add("p2");
        AbstractPhaseInterceptor p3 = setUpPhaseInterceptor("phase1", "p3", before1, true);
        control.replay();
        chain.add(p3);
        chain.add(p1);
        chain.add(p2);
        
        Iterator<Interceptor<? extends Message>> it = chain.iterator();

        assertSame("Unexpected interceptor at this position.", p3, it.next());
        assertSame("Unexpected interceptor at this position.", p2, it.next());
        assertSame("Unexpected interceptor at this position.", p1, it.next());
        assertTrue(!it.hasNext());
    }

    @Test
    public void testSingleInterceptorPass() {
        AbstractPhaseInterceptor p = setUpPhaseInterceptor("phase1", "p1");
        setUpPhaseInterceptorInvocations(p, false, false);
        control.replay();
        chain.add(p);
        chain.doIntercept(message);
    }

    @Test
    public void testSingleInterceptorFail() {
        AbstractPhaseInterceptor p = setUpPhaseInterceptor("phase1", "p1");
        setUpPhaseInterceptorInvocations(p, true, true);
        control.replay();
        chain.add(p);
        chain.doIntercept(message);
    }

    @Test
    public void testTwoInterceptorsInSamePhasePass() {
        AbstractPhaseInterceptor p1 = setUpPhaseInterceptor("phase1", "p1");
        setUpPhaseInterceptorInvocations(p1, false, false);
        AbstractPhaseInterceptor p2 = setUpPhaseInterceptor("phase1", "p2");
        setUpPhaseInterceptorInvocations(p2, false, false);
        control.replay();
        chain.add(p2);
        chain.add(p1);
        chain.doIntercept(message);
    }

    @Test
    public void testThreeInterceptorsInSamePhaseSecondFail() {
        AbstractPhaseInterceptor p1 = setUpPhaseInterceptor("phase1", "p1");
        setUpPhaseInterceptorInvocations(p1, false, true);
        AbstractPhaseInterceptor p2 = setUpPhaseInterceptor("phase1", "p2");
        setUpPhaseInterceptorInvocations(p2, true, true);
        AbstractPhaseInterceptor p3 = setUpPhaseInterceptor("phase1", "p3");
        control.replay();
        chain.add(p3);
        chain.add(p2);
        chain.add(p1);
        chain.doIntercept(message);
    }

    @Test
    public void testTwoInterceptorsInSamePhaseSecondFail() {
        AbstractPhaseInterceptor p1 = setUpPhaseInterceptor("phase1", "p1");
        setUpPhaseInterceptorInvocations(p1, false, true);
        AbstractPhaseInterceptor p2 = setUpPhaseInterceptor("phase1", "p2");
        setUpPhaseInterceptorInvocations(p2, true, true);
        control.replay();
        chain.add(p2);
        chain.add(p1);
        chain.doIntercept(message);
    }

    @Test
    public void testTwoInterceptorsInDifferentPhasesPass() {
        AbstractPhaseInterceptor p1 = setUpPhaseInterceptor("phase1", "p1");
        setUpPhaseInterceptorInvocations(p1, false, false);
        AbstractPhaseInterceptor p2 = setUpPhaseInterceptor("phase2", "p2");
        setUpPhaseInterceptorInvocations(p2, false, false);
        control.replay();
        chain.add(p1);
        chain.add(p2);
        chain.doIntercept(message);
    }

    @Test
    public void testTwoInterceptorsInDifferentPhasesSecondFail() {
        AbstractPhaseInterceptor p1 = setUpPhaseInterceptor("phase1", "p1");
        setUpPhaseInterceptorInvocations(p1, false, true);
        AbstractPhaseInterceptor p2 = setUpPhaseInterceptor("phase2", "p2");
        setUpPhaseInterceptorInvocations(p2, true, true);
        control.replay();
        chain.add(p1);
        chain.add(p2);
        chain.doIntercept(message);
    }

    @Test
    public void testInsertionInDifferentPhasePass() {

        AbstractPhaseInterceptor p2 = setUpPhaseInterceptor("phase2", "p2");
        setUpPhaseInterceptorInvocations(p2, false, false);
        AbstractPhaseInterceptor p3 = setUpPhaseInterceptor("phase3", "p3");
        setUpPhaseInterceptorInvocations(p3, false, false);
        InsertingPhaseInterceptor p1 = new InsertingPhaseInterceptor(chain, p2,
                "phase1", "p1");
        control.replay();
        chain.add(p3);
        chain.add(p1);
        chain.doIntercept(message);
        assertEquals(1, p1.invoked);
        assertEquals(0, p1.faultInvoked);
    }

    @Test
    public void testInsertionInSamePhasePass() {

        AbstractPhaseInterceptor p2 = setUpPhaseInterceptor("phase1", "p2");
        setUpPhaseInterceptorInvocations(p2, false, false);
        Set<String> after3 = new HashSet<String>();
        after3.add("p2");
        AbstractPhaseInterceptor p3 = setUpPhaseInterceptor("phase1", "p3",
                after3);
        setUpPhaseInterceptorInvocations(p3, false, false);
        InsertingPhaseInterceptor p1 = new InsertingPhaseInterceptor(chain, p3,
                "phase1", "p1");
        p1.addBefore("p2");
        control.replay();
        chain.add(p1);
        chain.add(p2);
        chain.doIntercept(message);
        assertEquals(1, p1.invoked);
        assertEquals(0, p1.faultInvoked);
    }

    @Test
    public void testWrappedInvocation() throws Exception {
        CountingPhaseInterceptor p1 = new CountingPhaseInterceptor("phase1",
                "p1");
        WrapperingPhaseInterceptor p2 = new WrapperingPhaseInterceptor(
                "phase2", "p2");
        CountingPhaseInterceptor p3 = new CountingPhaseInterceptor("phase3",
                "p3");

        message.getInterceptorChain();
        EasyMock.expectLastCall().andReturn(chain).anyTimes();

        control.replay();
        chain.add(p1);
        chain.add(p2);
        chain.add(p3);
        chain.doIntercept(message);
        assertEquals(1, p1.invoked);
        assertEquals(1, p2.invoked);
        assertEquals(1, p3.invoked);
    }

    @Test
    public void testChainInvocationStartFromSpecifiedInterceptor() throws Exception {
        CountingPhaseInterceptor p1 = new CountingPhaseInterceptor("phase1",
                "p1");
        CountingPhaseInterceptor p2 = new CountingPhaseInterceptor(
                "phase2", "p2");
        CountingPhaseInterceptor p3 = new CountingPhaseInterceptor("phase3",
                "p3");

        message.getInterceptorChain();
        EasyMock.expectLastCall().andReturn(chain).anyTimes();

        control.replay();
        chain.add(p1);
        chain.add(p2);
        chain.add(p3);
        chain.doInterceptStartingAfter(message, p2.getId());
        assertEquals(0, p1.invoked);
        assertEquals(0, p2.invoked);
        assertEquals(1, p3.invoked);
    }
    
    AbstractPhaseInterceptor setUpPhaseInterceptor(String phase, String id) {
        return setUpPhaseInterceptor(phase, id, null);
    }

    AbstractPhaseInterceptor setUpPhaseInterceptor(String phase, String id,
            Set<String> a) {
        AbstractPhaseInterceptor p = control
                .createMock(AbstractPhaseInterceptor.class);
        EasyMock.expect(p.getPhase()).andReturn(phase).anyTimes();
        EasyMock.expect(p.getId()).andReturn(id).anyTimes();
        Set<String> before = new HashSet<String>();
        Set<String> after = null == a ? new HashSet<String>() : a;
        EasyMock.expect(p.getBefore()).andReturn(before).anyTimes();
        EasyMock.expect(p.getAfter()).andReturn(after).anyTimes();

        return p;
    }
    
    @SuppressWarnings("unchecked")
    void setUpPhaseInterceptorInvocations(AbstractPhaseInterceptor p,
            boolean fail, boolean expectFault) {
        p.handleMessage(message);
        if (fail) {
            EasyMock.expectLastCall().andThrow(new RuntimeException());
            message.setContent(EasyMock.isA(Class.class),
                               EasyMock.isA(Exception.class));
            EasyMock.expectLastCall();
        } else {
            EasyMock.expectLastCall();
        }
        if (expectFault) {
            p.handleFault(message);
            EasyMock.expectLastCall();
        }
    }

    public class InsertingPhaseInterceptor extends
            AbstractPhaseInterceptor<Message> {
        int invoked;

        int faultInvoked;

        private final PhaseInterceptorChain insertionChain;

        private final AbstractPhaseInterceptor insertionInterceptor;

        public InsertingPhaseInterceptor(PhaseInterceptorChain c,
                AbstractPhaseInterceptor i, String phase, String id) {
            setPhase(phase);
            setId(id);
            insertionChain = c;
            insertionInterceptor = i;
        }

        public void handleMessage(Message m) {
            insertionChain.add(insertionInterceptor);
            invoked++;
        }

        public void handleFault(Message m) {
            faultInvoked++;
        }
    }

    public class CountingPhaseInterceptor extends
            AbstractPhaseInterceptor<Message> {
        int invoked;

        public CountingPhaseInterceptor(String phase, String id) {
            setPhase(phase);
            setId(id);
        }

        public void handleMessage(Message m) {
            invoked++;
        }
    }

    public class WrapperingPhaseInterceptor extends CountingPhaseInterceptor {
        public WrapperingPhaseInterceptor(String phase, String id) {
            super(phase, id);
        }

        public void handleMessage(Message m) {
            super.handleMessage(m);
            m.getInterceptorChain().doIntercept(m);
        }
    }


}
