package org.objectweb.celtix.jbi.se.state;

import javax.jbi.JBIException;

import junit.framework.TestCase;

import org.objectweb.celtix.jbi.se.state.ServiceEngineStateMachine.SEOperation;

public class ServiceEngineShutdownTest extends TestCase {

    private ServiceEngineStateFactory stateFactory;
    private ServiceEngineStateMachine shutdown;
    
    
    public void setUp() throws Exception {
        stateFactory = ServiceEngineStateFactory.getInstance();
        shutdown = stateFactory.getShutdownState();
    }
    
    public void testInitOperation() throws Exception {
        shutdown.changeState(SEOperation.init, null);
        assertTrue(stateFactory.getCurrentState() instanceof ServiceEngineStop);
    }
    
    public void testStartOperation() throws Exception {
        try {
            shutdown.changeState(SEOperation.start, null);
        } catch (JBIException e) {
            return;
        }
        fail();
    }
    
    public void testStopOperation() throws Exception {
        try {
            shutdown.changeState(SEOperation.stop, null);
        } catch (JBIException e) {
            return;
        }
        fail();
    }
    
    public void testShutdownOperation() throws Exception {
        try {
            shutdown.changeState(SEOperation.shutdown, null);
        } catch (JBIException e) {
            return;
        }
        fail();
    }
}
