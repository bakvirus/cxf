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
package org.apache.cxf.ws.security.wss4j;

import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.xml.namespace.QName;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.dom.DOMSource;

import org.apache.cxf.binding.soap.SoapFault;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.binding.soap.SoapVersion;
import org.apache.cxf.binding.soap.saaj.SAAJInInterceptor;
import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.common.i18n.Message;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.common.security.UsernameToken;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.MessageUtils;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.security.SecurityContext;
import org.apache.cxf.staxutils.StaxUtils;
import org.apache.cxf.ws.security.SecurityConstants;
import org.apache.cxf.ws.security.tokenstore.SecurityToken;
import org.apache.cxf.ws.security.tokenstore.TokenStore;
import org.apache.ws.security.WSConstants;
import org.apache.ws.security.WSPasswordCallback;
import org.apache.ws.security.WSSConfig;
import org.apache.ws.security.WSSecurityEngine;
import org.apache.ws.security.WSSecurityEngineResult;
import org.apache.ws.security.WSSecurityException;
import org.apache.ws.security.WSUsernameTokenPrincipal;
import org.apache.ws.security.handler.RequestData;
import org.apache.ws.security.handler.WSHandlerConstants;
import org.apache.ws.security.handler.WSHandlerResult;
import org.apache.ws.security.message.token.SecurityTokenReference;
import org.apache.ws.security.processor.Processor;
import org.apache.ws.security.util.WSSecurityUtil;
import org.apache.ws.security.validate.NoOpValidator;
import org.apache.ws.security.validate.Validator;

/**
 * Performs WS-Security inbound actions.
 * 
 * @author <a href="mailto:tsztelak@gmail.com">Tomasz Sztelak</a>
 */
public class WSS4JInInterceptor extends AbstractWSS4JInterceptor {

    public static final String TIMESTAMP_RESULT = "wss4j.timestamp.result";
    public static final String SIGNATURE_RESULT = "wss4j.signature.result";
    public static final String PRINCIPAL_RESULT = "wss4j.principal.result";
    public static final String PROCESSOR_MAP = "wss4j.processor.map";
    public static final String VALIDATOR_MAP = "wss4j.validator.map";

    public static final String SECURITY_PROCESSED = WSS4JInInterceptor.class.getName() + ".DONE";
    
    private static final Logger LOG = LogUtils.getL7dLogger(WSS4JInInterceptor.class);
    private static final Logger TIME_LOG = LogUtils.getL7dLogger(WSS4JInInterceptor.class,
                                                                 null,
                                                                 WSS4JInInterceptor.class.getName()
                                                                     + "-Time");
    private SAAJInInterceptor saajIn = new SAAJInInterceptor();
    private boolean ignoreActions;

    /**
     *
     */
    private WSSecurityEngine secEngineOverride;
    
    public WSS4JInInterceptor() {
        super();

        setPhase(Phase.PRE_PROTOCOL);
        getAfter().add(SAAJInInterceptor.class.getName());
    }
    public WSS4JInInterceptor(boolean ignore) {
        this();
        ignoreActions = ignore;
    }

    public WSS4JInInterceptor(Map<String, Object> properties) {
        this();
        setProperties(properties);
        final Map<QName, Object> processorMap = CastUtils.cast(
            (Map<?, ?>)properties.get(PROCESSOR_MAP));
        final Map<QName, Object> validatorMap = CastUtils.cast(
            (Map<?, ?>)properties.get(VALIDATOR_MAP));
        
        if (processorMap != null) {
            if (validatorMap != null) {
                processorMap.putAll(validatorMap);
            }
            secEngineOverride = createSecurityEngine(processorMap);
        } else if (validatorMap != null) {
            if (processorMap != null) {
                validatorMap.putAll(processorMap);
            }
            secEngineOverride = createSecurityEngine(validatorMap);
        }
    }

    public void setIgnoreActions(boolean i) {
        ignoreActions = i;
    }
    private SOAPMessage getSOAPMessage(SoapMessage msg) {
        SOAPMessage doc = msg.getContent(SOAPMessage.class);
        if (doc == null) {
            saajIn.handleMessage(msg);
            doc = msg.getContent(SOAPMessage.class);
        }
        return doc;
    }
    
    @Override
    public Object getProperty(Object msgContext, String key) {
        // use the superclass first
        Object result = super.getProperty(msgContext, key);
        
        // handle the special case of the SEND_SIGV
        if (result == null 
            && key == WSHandlerConstants.SEND_SIGV
            && this.isRequestor((SoapMessage)msgContext)) {
            result = ((SoapMessage)msgContext).getExchange().getOutMessage().get(key);
        }               
        return result;
    }
    
    public void handleMessage(SoapMessage msg) throws Fault {
        if (msg.containsKey(SECURITY_PROCESSED)) {
            return;
        }
        msg.put(SECURITY_PROCESSED, Boolean.TRUE);
        
        boolean utWithCallbacks = 
            !MessageUtils.getContextualBoolean(msg, SecurityConstants.USERNAME_TOKEN_NO_CALLBACKS, false);
        
        WSSConfig config = (WSSConfig)msg.getContextualProperty(WSSConfig.class.getName()); 
        WSSecurityEngine engine;
        if (config != null) {
            engine = new WSSecurityEngine();
            engine.setWssConfig(config);
        } else {
            engine = getSecurityEngine(utWithCallbacks);
        }
        
        SOAPMessage doc = getSOAPMessage(msg);
        
        boolean doDebug = LOG.isLoggable(Level.FINE);
        boolean doTimeLog = TIME_LOG.isLoggable(Level.FINE);

        SoapVersion version = msg.getVersion();
        if (doDebug) {
            LOG.fine("WSS4JInInterceptor: enter handleMessage()");
        }

        long t0 = 0;
        long t1 = 0;
        long t2 = 0;
        long t3 = 0;

        if (doTimeLog) {
            t0 = System.currentTimeMillis();
        }

        RequestData reqData = new RequestData();
        reqData.setWssConfig(engine.getWssConfig());
        /*
         * The overall try, just to have a finally at the end to perform some
         * housekeeping.
         */
        try {
            reqData.setMsgContext(msg);
            computeAction(msg, reqData);
            List<Integer> actions = new ArrayList<Integer>();
            String action = getAction(msg, version);

            int doAction = WSSecurityUtil.decodeAction(action, actions);

            String actor = (String)getOption(WSHandlerConstants.ACTOR);

            CallbackHandler cbHandler = getCallback(reqData, doAction, utWithCallbacks);
            
            String passwordTypeStrict = (String)getOption(WSHandlerConstants.PASSWORD_TYPE_STRICT);
            if (passwordTypeStrict == null) {
                setProperty(WSHandlerConstants.PASSWORD_TYPE_STRICT, "true");
            }

            /*
             * Get and check the Signature specific parameters first because
             * they may be used for encryption too.
             */
            doReceiverAction(doAction, reqData);
            
            if (doTimeLog) {
                t1 = System.currentTimeMillis();
            }

            List<WSSecurityEngineResult> wsResult = engine.processSecurityHeader(
                doc.getSOAPPart(), 
                actor, 
                cbHandler, 
                reqData.getSigCrypto(), 
                reqData.getDecCrypto()
            );

            if (doTimeLog) {
                t2 = System.currentTimeMillis();
            }

            if (wsResult != null) { // security header found
                if (reqData.getWssConfig().isEnableSignatureConfirmation()) {
                    checkSignatureConfirmation(reqData, wsResult);
                }

                storeSignature(msg, reqData, wsResult);
                storeTimestamp(msg, reqData, wsResult);
                checkActions(msg, reqData, wsResult, actions);
                doResults(msg, actor, doc, wsResult, utWithCallbacks);
            } else { // no security header found
                // Create an empty result list to pass into the required validation
                // methods.
                wsResult = new ArrayList<WSSecurityEngineResult>();
                
                if (doc.getSOAPPart().getEnvelope().getBody().hasFault()) {
                    LOG.warning("Request does not contain Security header, " 
                                + "but it's a fault.");
                    // We allow lax action matching here for backwards compatibility
                    // with manually configured WSS4JInInterceptors that previously
                    // allowed faults to pass through even if their actions aren't
                    // a strict match against those configured.  In the WS-SP case,
                    // we will want to still call doResults as it handles asserting
                    // certain assertions that do not require a WS-S header such as
                    // a sp:TransportBinding assertion.  In the case of WS-SP,
                    // the unasserted assertions will provide confirmation that
                    // security was not sufficient.
                    // checkActions(msg, reqData, wsResult, actions);
                    doResults(msg, actor, doc, wsResult);
                } else {
                    checkActions(msg, reqData, wsResult, actions);
                    doResults(msg, actor, doc, wsResult);
                }
            }

            if (doTimeLog) {
                t3 = System.currentTimeMillis();
                TIME_LOG.fine("Receive request: total= " + (t3 - t0) 
                        + " request preparation= " + (t1 - t0)
                        + " request processing= " + (t2 - t1) 
                        + " header, cert verify, timestamp= " + (t3 - t2) + "\n");
            }

            if (doDebug) {
                LOG.fine("WSS4JInInterceptor: exit handleMessage()");
            }

        } catch (WSSecurityException e) {
            LOG.log(Level.WARNING, "", e);
            SoapFault fault = createSoapFault(version, e);
            throw fault;
        } catch (XMLStreamException e) {
            throw new SoapFault(new Message("STAX_EX", LOG), e, version.getSender());
        } catch (SOAPException e) {
            throw new SoapFault(new Message("SAAJ_EX", LOG), e, version.getSender());
        } finally {
            reqData.clear();
            reqData = null;
        }
    }

    private void checkActions(
        SoapMessage msg, 
        RequestData reqData, 
        List<WSSecurityEngineResult> wsResult, 
        List<Integer> actions
    ) throws WSSecurityException {
        /*
         * now check the security actions: do they match, in any order?
         */
        if (!ignoreActions && !checkReceiverResultsAnyOrder(wsResult, actions)) {
            LOG.warning("Security processing failed (actions mismatch)");
            throw new WSSecurityException(WSSecurityException.INVALID_SECURITY);
        }
    }
    
    private void storeSignature(
        SoapMessage msg, RequestData reqData, List<WSSecurityEngineResult> wsResult
    ) throws WSSecurityException {
        // Extract the signature action result from the action list
        List<WSSecurityEngineResult> signatureResults = new ArrayList<WSSecurityEngineResult>();
        signatureResults = 
            WSSecurityUtil.fetchAllActionResults(wsResult, WSConstants.SIGN, signatureResults);

        // Store the last signature result
        if (!signatureResults.isEmpty()) {
            msg.put(SIGNATURE_RESULT, signatureResults.get(signatureResults.size() - 1));
        }
    }
    
    private void storeTimestamp(
        SoapMessage msg, RequestData reqData, List<WSSecurityEngineResult> wsResult
    ) throws WSSecurityException {
        // Extract the timestamp action result from the action list
        List<WSSecurityEngineResult> timestampResults = new ArrayList<WSSecurityEngineResult>();
        timestampResults = 
            WSSecurityUtil.fetchAllActionResults(wsResult, WSConstants.TS, timestampResults);

        if (!timestampResults.isEmpty()) {
            msg.put(TIMESTAMP_RESULT, timestampResults.get(timestampResults.size() - 1));
        }
    }
    
    /**
     * Do whatever is necessary to determine the action for the incoming message and 
     * do whatever other setup work is necessary.
     * 
     * @param msg
     * @param reqData
     */
    protected void computeAction(SoapMessage msg, RequestData reqData) {
        
    }

    protected void doResults(
        SoapMessage msg, String actor, SOAPMessage doc, List<WSSecurityEngineResult> wsResult
    ) throws SOAPException, XMLStreamException, WSSecurityException {
        doResults(msg, actor, doc, wsResult, false);
    }

    protected void doResults(
        SoapMessage msg, String actor, SOAPMessage doc, List<WSSecurityEngineResult> wsResult, 
        boolean utWithCallbacks
    ) throws SOAPException, XMLStreamException, WSSecurityException {
        /*
         * All ok up to this point. Now construct and setup the security result
         * structure. The service may fetch this and check it.
         */
        List<WSHandlerResult> results = CastUtils.cast((List<?>)msg.get(WSHandlerConstants.RECV_RESULTS));
        if (results == null) {
            results = new ArrayList<WSHandlerResult>();
            msg.put(WSHandlerConstants.RECV_RESULTS, results);
        }
        WSHandlerResult rResult = new WSHandlerResult(actor, wsResult);
        results.add(0, rResult);

        SOAPBody body = doc.getSOAPBody();

        XMLStreamReader reader = StaxUtils.createXMLStreamReader(new DOMSource(body));
        // advance just past body
        int evt = reader.next();
        int i = 0;
        while (reader.hasNext() && i < 1
               && (evt != XMLStreamConstants.END_ELEMENT || evt != XMLStreamConstants.START_ELEMENT)) {
            reader.next();
            i++;
        }
        msg.setContent(XMLStreamReader.class, reader);
        for (WSSecurityEngineResult o : wsResult) {
            final Principal p = (Principal)o.get(WSSecurityEngineResult.TAG_PRINCIPAL);
            if (p != null) {
                msg.put(PRINCIPAL_RESULT, p);
                if (!utWithCallbacks && p instanceof WSUsernameTokenPrincipal) {
                    WSUsernameTokenPrincipal utp = (WSUsernameTokenPrincipal)p;
                    msg.put(org.apache.cxf.common.security.SecurityToken.class, 
                            new UsernameToken(utp.getName(),
                                              utp.getPassword(),
                                              utp.getPasswordType(),
                                              utp.isPasswordDigest(),
                                              utp.getNonce(),
                                              utp.getCreatedTime()));
                    
                }
                SecurityContext sc = msg.get(SecurityContext.class);
                if (sc == null || sc.getUserPrincipal() == null) {
                    msg.put(SecurityContext.class, createSecurityContext(p));
                    break;
                }
            }            
        }
    }

    
    protected SecurityContext createSecurityContext(final Principal p) {
        return new SecurityContext() {
            public Principal getUserPrincipal() {
                return p;
            }
            public boolean isUserInRole(String role) {
                return false;
            }
        };
    }
    
    private String getAction(SoapMessage msg, SoapVersion version) {
        String action = (String)getOption(WSHandlerConstants.ACTION);
        if (action == null) {
            action = (String)msg.get(WSHandlerConstants.ACTION);
        }
        if (action == null) {
            LOG.warning("No security action was defined!");
            throw new SoapFault("No security action was defined!", version.getReceiver());
        }
        return action;
    }
    
    private class TokenStoreCallbackHandler implements CallbackHandler {
        private CallbackHandler internal;
        private TokenStore store;
        public TokenStoreCallbackHandler(CallbackHandler in,
                                         TokenStore st) {
            internal = in;
            store = st;
        }
        
        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            for (int i = 0; i < callbacks.length; i++) {
                WSPasswordCallback pc = (WSPasswordCallback)callbacks[i];
                
                String id = pc.getIdentifier();
                
                if (SecurityTokenReference.ENC_KEY_SHA1_URI.equals(pc.getKeyType())) {
                    for (SecurityToken token : store.getValidTokens()) {
                        if (id.equals(token.getSHA1())) {
                            pc.setKey(token.getSecret());
                            return;
                        }
                    }                    
                } else { 
                    SecurityToken tok = store.getToken(id);
                    if (tok != null) {
                        pc.setKey(tok.getSecret());
                        pc.setCustomToken(tok.getToken());
                        return;
                    }
                }
            }
            if (internal != null) {
                internal.handle(callbacks);
            }
        }
        
    }

    protected CallbackHandler getCallback(RequestData reqData, int doAction, boolean utWithCallbacks) 
        throws WSSecurityException {
        if (!utWithCallbacks 
            && ((doAction & WSConstants.UT) != 0 || (doAction & WSConstants.UT_UNKNOWN) != 0)) {
            CallbackHandler pwdCallback = null;
            try {
                pwdCallback = getCallback(reqData, doAction);
            } catch (Exception ex) {
                // ignore
            }
            return new DelegatingCallbackHandler(pwdCallback);
        } else {
            return getCallback(reqData, doAction);
        }
    }
    
    protected CallbackHandler getCallback(RequestData reqData, int doAction) throws WSSecurityException {
        /*
         * To check a UsernameToken or to decrypt an encrypted message we need a
         * password.
         */
        CallbackHandler cbHandler = null;
        if ((doAction & (WSConstants.ENCR | WSConstants.UT)) != 0) {
            Object o = ((SoapMessage)reqData.getMsgContext())
                .getContextualProperty(SecurityConstants.CALLBACK_HANDLER);
            if (o instanceof String) {
                try {
                    o = ClassLoaderUtils.loadClass((String)o, this.getClass()).newInstance();
                } catch (Exception e) {
                    throw new WSSecurityException(e.getMessage(), e);
                }
            }            
            if (o instanceof CallbackHandler) {
                cbHandler = (CallbackHandler)o;
            }
            if (cbHandler == null) {
                try {
                    cbHandler = getPasswordCB(reqData);
                } catch (WSSecurityException sec) {
                    Endpoint ep = ((SoapMessage)reqData.getMsgContext()).getExchange().get(Endpoint.class);
                    if (ep != null && ep.getEndpointInfo() != null) {
                        TokenStore store = (TokenStore)ep.getEndpointInfo()
                            .getProperty(TokenStore.class.getName());
                        if (store != null) {
                            return new TokenStoreCallbackHandler(cbHandler, store);
                        }
                    }                    
                    throw sec;
                }
            }
        }
        Endpoint ep = ((SoapMessage)reqData.getMsgContext()).getExchange().get(Endpoint.class);
        if (ep != null && ep.getEndpointInfo() != null) {
            TokenStore store = (TokenStore)ep.getEndpointInfo().getProperty(TokenStore.class.getName());
            if (store != null) {
                return new TokenStoreCallbackHandler(cbHandler, store);
            }
        }
        return cbHandler;
    }


    
    /**
     * @return      the WSSecurityEngine in use by this interceptor.
     *              This engine is defined to be the secEngineOverride
     *              instance, if defined in this class (and supplied through
     *              construction); otherwise, it is taken to be the default
     *              WSSecEngine instance (currently defined in the WSHandler
     *              base class).
     */
    protected WSSecurityEngine getSecurityEngine(boolean utWithCallbacks) {
        if (secEngineOverride != null) {
            return secEngineOverride;
        }
        
        if (!utWithCallbacks) {
            Map<QName, Object> profiles = new HashMap<QName, Object>(1);
            Validator validator = new NoOpValidator();
            profiles.put(WSSecurityEngine.USERNAME_TOKEN, validator);
            return createSecurityEngine(profiles);
        }
        
        return secEngine;
    }

    /**
     * @return      a freshly minted WSSecurityEngine instance, using the
     *              (non-null) processor map, to be used to initialize the
     *              WSSecurityEngine instance.
     */
    protected static WSSecurityEngine
    createSecurityEngine(
        final Map<QName, Object> map
    ) {
        assert map != null;
        final WSSConfig config = WSSConfig.getNewInstance();
        for (Map.Entry<QName, Object> entry : map.entrySet()) {
            final QName key = entry.getKey();
            Object val = entry.getValue();
            if (val instanceof Class<?>) {
                config.setProcessor(key, (Class<?>)val);
            } else if (val instanceof Processor) {
                config.setProcessor(key, (Processor)val);
            } else if (val instanceof Validator) {
                config.setValidator(key, (Validator)val);
            } else if (val == null) {
                config.setProcessor(key, (Class<?>)val);
            }
        }
        final WSSecurityEngine ret = new WSSecurityEngine();
        ret.setWssConfig(config);
        return ret;
    }
    
    
    /**
     * Create a SoapFault from a WSSecurityException, following the SOAP Message Security
     * 1.1 specification, chapter 12 "Error Handling".
     * 
     * When the Soap version is 1.1 then set the Fault/Code/Value from the fault code
     * specified in the WSSecurityException (if it exists).
     * 
     * Otherwise set the Fault/Code/Value to env:Sender and the Fault/Code/Subcode/Value
     * as the fault code from the WSSecurityException.
     */
    private SoapFault 
    createSoapFault(SoapVersion version, WSSecurityException e) {
        SoapFault fault;
        javax.xml.namespace.QName faultCode = e.getFaultCode();
        if (version.getVersion() == 1.1 && faultCode != null) {
            fault = new SoapFault(e.getMessage(), e, faultCode);
        } else {
            fault = new SoapFault(e.getMessage(), e, version.getSender());
            if (version.getVersion() != 1.1 && faultCode != null) {
                fault.setSubCode(faultCode);
            }
        }
        return fault;
    }
    
}
