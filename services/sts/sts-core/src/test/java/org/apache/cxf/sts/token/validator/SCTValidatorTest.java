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
package org.apache.cxf.sts.token.validator;

import java.util.Properties;

import org.w3c.dom.Document;

import org.apache.cxf.helpers.DOMUtils;
import org.apache.cxf.jaxws.context.WebServiceContextImpl;
import org.apache.cxf.jaxws.context.WrappedMessageContext;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.sts.STSConstants;
import org.apache.cxf.sts.StaticSTSProperties;
import org.apache.cxf.sts.cache.DefaultInMemoryTokenStore;
import org.apache.cxf.sts.cache.STSTokenStore;
import org.apache.cxf.sts.common.PasswordCallbackHandler;
import org.apache.cxf.sts.request.KeyRequirements;
import org.apache.cxf.sts.request.ReceivedToken;
import org.apache.cxf.sts.request.TokenRequirements;
import org.apache.cxf.sts.service.EncryptionProperties;
import org.apache.cxf.sts.token.provider.SCTProvider;
import org.apache.cxf.sts.token.provider.TokenProvider;
import org.apache.cxf.sts.token.provider.TokenProviderParameters;
import org.apache.cxf.sts.token.provider.TokenProviderResponse;
import org.apache.cxf.ws.security.trust.STSUtils;
import org.apache.ws.security.CustomTokenPrincipal;
import org.apache.ws.security.WSSecurityException;
import org.apache.ws.security.components.crypto.Crypto;
import org.apache.ws.security.components.crypto.CryptoFactory;
import org.apache.ws.security.message.token.SecurityContextToken;


/**
 * Some unit tests for validating a SecurityContextToken via the SCTValidator.
 */
public class SCTValidatorTest extends org.junit.Assert {
    
    private static STSTokenStore tokenStore = new DefaultInMemoryTokenStore();
    
    /**
     * Test a valid SecurityContextToken
     */
    @org.junit.Test
    public void testValidSecurityContextToken() throws Exception {
        TokenValidator sctValidator = new SCTValidator();
        TokenValidatorParameters validatorParameters = createValidatorParameters();
        TokenRequirements tokenRequirements = validatorParameters.getTokenRequirements();
        
        // Create a ValidateTarget consisting of a SecurityContextToken
        TokenProviderResponse providerResponse = getSecurityContextToken();
        ReceivedToken validateTarget = new ReceivedToken(providerResponse.getToken());
        tokenRequirements.setValidateTarget(validateTarget);
        
        assertTrue(sctValidator.canHandleToken(validateTarget));
        
        TokenValidatorResponse validatorResponse = 
            sctValidator.validateToken(validatorParameters);
        assertTrue(validatorResponse != null);
        assertTrue(validatorResponse.isValid());
        assertTrue(
            validatorResponse.getAdditionalProperties().get(SCTValidator.SCT_VALIDATOR_SECRET) != null
        );
        assertTrue(validatorResponse.getPrincipal().getName().equals("alice"));
        
        // Now remove the SCT from the cache
        tokenStore.remove(tokenStore.getToken(providerResponse.getTokenId()));
        assertNull(tokenStore.getToken(providerResponse.getTokenId()));
        validatorResponse = sctValidator.validateToken(validatorParameters);
        assertTrue(validatorResponse != null);
        assertFalse(validatorResponse.isValid());
    }
    
    /**
     * Test an invalid SecurityContextToken
     */
    @org.junit.Test
    public void testInvalidSecurityContextToken() throws Exception {
        TokenValidator sctValidator = new SCTValidator();
        TokenValidatorParameters validatorParameters = createValidatorParameters();
        TokenRequirements tokenRequirements = validatorParameters.getTokenRequirements();
        
        // Create a ValidateTarget consisting of a SecurityContextToken
        Document doc = DOMUtils.createDocument();
        SecurityContextToken sct = new SecurityContextToken(doc);
        ReceivedToken validateTarget = new ReceivedToken(sct.getElement());
        tokenRequirements.setValidateTarget(validateTarget);
        
        assertTrue(sctValidator.canHandleToken(validateTarget));
        
        TokenValidatorResponse validatorResponse = 
            sctValidator.validateToken(validatorParameters);
        assertTrue(validatorResponse != null);
        assertFalse(validatorResponse.isValid());
    }
    
    private TokenProviderResponse getSecurityContextToken() throws Exception {
        TokenProvider sctTokenProvider = new SCTProvider();
        
        TokenProviderParameters providerParameters = 
            createProviderParameters(STSUtils.TOKEN_TYPE_SCT_05_12);
        
        return sctTokenProvider.createToken(providerParameters);
    }
    
    private TokenValidatorParameters createValidatorParameters() throws WSSecurityException {
        TokenValidatorParameters parameters = new TokenValidatorParameters();
        
        TokenRequirements tokenRequirements = new TokenRequirements();
        tokenRequirements.setTokenType(STSConstants.STATUS);
        parameters.setTokenRequirements(tokenRequirements);
        
        KeyRequirements keyRequirements = new KeyRequirements();
        parameters.setKeyRequirements(keyRequirements);
        parameters.setTokenStore(tokenStore);
        
        parameters.setPrincipal(new CustomTokenPrincipal("alice"));
        // Mock up message context
        MessageImpl msg = new MessageImpl();
        WrappedMessageContext msgCtx = new WrappedMessageContext(msg);
        WebServiceContextImpl webServiceContext = new WebServiceContextImpl(msgCtx);
        parameters.setWebServiceContext(webServiceContext);
        
        // Add STSProperties object
        StaticSTSProperties stsProperties = new StaticSTSProperties();
        Crypto crypto = CryptoFactory.getInstance(getEncryptionProperties());
        stsProperties.setEncryptionCrypto(crypto);
        stsProperties.setSignatureCrypto(crypto);
        stsProperties.setEncryptionUsername("myservicekey");
        stsProperties.setSignatureUsername("mystskey");
        stsProperties.setCallbackHandler(new PasswordCallbackHandler());
        stsProperties.setIssuer("STS");
        parameters.setStsProperties(stsProperties);
        
        return parameters;
    }
    
    private TokenProviderParameters createProviderParameters(String tokenType) throws WSSecurityException {
        TokenProviderParameters parameters = new TokenProviderParameters();
        
        TokenRequirements tokenRequirements = new TokenRequirements();
        tokenRequirements.setTokenType(tokenType);
        parameters.setTokenRequirements(tokenRequirements);
        
        KeyRequirements keyRequirements = new KeyRequirements();
        parameters.setKeyRequirements(keyRequirements);

        parameters.setTokenStore(tokenStore);
        
        parameters.setPrincipal(new CustomTokenPrincipal("alice"));
        // Mock up message context
        MessageImpl msg = new MessageImpl();
        WrappedMessageContext msgCtx = new WrappedMessageContext(msg);
        WebServiceContextImpl webServiceContext = new WebServiceContextImpl(msgCtx);
        parameters.setWebServiceContext(webServiceContext);
        
        parameters.setAppliesToAddress("http://dummy-service.com/dummy");
        
        // Add STSProperties object
        StaticSTSProperties stsProperties = new StaticSTSProperties();
        Crypto crypto = CryptoFactory.getInstance(getEncryptionProperties());
        stsProperties.setSignatureCrypto(crypto);
        stsProperties.setSignatureUsername("mystskey");
        stsProperties.setCallbackHandler(new PasswordCallbackHandler());
        stsProperties.setIssuer("STS");
        parameters.setStsProperties(stsProperties);
        
        parameters.setEncryptionProperties(new EncryptionProperties());
        
        return parameters;
    }
    
    private Properties getEncryptionProperties() {
        Properties properties = new Properties();
        properties.put(
            "org.apache.ws.security.crypto.provider", "org.apache.ws.security.components.crypto.Merlin"
        );
        properties.put("org.apache.ws.security.crypto.merlin.keystore.password", "stsspass");
        properties.put("org.apache.ws.security.crypto.merlin.keystore.file", "stsstore.jks");
        
        return properties;
    }
    
    
}
