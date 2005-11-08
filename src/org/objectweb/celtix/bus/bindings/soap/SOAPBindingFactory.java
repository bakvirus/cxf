package org.objectweb.celtix.bus.bindings.soap;

import java.io.IOException;
import javax.wsdl.WSDLException;
import javax.xml.ws.Endpoint;

import org.objectweb.celtix.Bus;
import org.objectweb.celtix.addressing.EndpointReferenceType;
import org.objectweb.celtix.bindings.BindingFactory;
import org.objectweb.celtix.bindings.ClientBinding;
import org.objectweb.celtix.bindings.DataBindingCallbackFactory;
import org.objectweb.celtix.bindings.ServerBinding;

public class SOAPBindingFactory implements BindingFactory {
    private Bus bus;
    
    public SOAPBindingFactory() {
        //Complete
    }
    
    public void init(Bus b) {
        bus = b;
    }
    
    public ClientBinding createClientBinding(EndpointReferenceType reference) 
        throws WSDLException, IOException {        
        return new SOAPClientBinding(bus, reference);
    }

    public ServerBinding createServerBinding(EndpointReferenceType reference,
                                             Endpoint ep,
                                             DataBindingCallbackFactory cbFactory)
        throws WSDLException, IOException {
        return new SOAPServerBinding(bus, reference, ep, cbFactory);
    }
}
