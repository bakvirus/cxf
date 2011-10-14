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
package org.apache.cxf.rs.security.oauth.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import net.oauth.OAuth;
import net.oauth.OAuthAccessor;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthMessage;
import net.oauth.OAuthProblemException;
import net.oauth.server.OAuthServlet;

import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.common.util.StringUtils;
import org.apache.cxf.jaxrs.impl.MetadataMap;
import org.apache.cxf.jaxrs.utils.FormUtils;
import org.apache.cxf.rs.security.oauth.data.Client;
import org.apache.cxf.rs.security.oauth.data.RequestToken;
import org.apache.cxf.rs.security.oauth.data.Token;
import org.apache.cxf.rs.security.oauth.provider.DefaultOAuthValidator;
import org.apache.cxf.rs.security.oauth.provider.OAuthDataProvider;

public final class OAuthUtils {

    private OAuthUtils() {
    }

    public static void validateMessage(OAuthMessage oAuthMessage, Client client, Token token) 
        throws Exception {
        OAuthConsumer consumer = new OAuthConsumer(null, client.getConsumerKey(),
            client.getSecretKey(), null);
        OAuthAccessor accessor = new OAuthAccessor(consumer);
        if (token != null) {
            if (token instanceof RequestToken) {
                accessor.requestToken = token.getTokenString(); 
            } else {
                accessor.accessToken = token.getTokenString();
            }
            accessor.tokenSecret = token.getTokenSecret();
        }
        
        DefaultOAuthValidator validator = new DefaultOAuthValidator(); 
        validator.validateMessage(oAuthMessage, accessor);
        if (token != null) {
            validator.validateToken(token);
        }
    }
    
    public static OAuthMessage getOAuthMessage(HttpServletRequest request,
                                               String[] requiredParams) throws Exception {
        OAuthMessage oAuthMessage = OAuthServlet.getMessage(request, request.getRequestURL().toString());
        OAuthUtils.addParametersIfNeeded(request, oAuthMessage);
        oAuthMessage.requireParameters(requiredParams);
        return oAuthMessage;
    }
    
    public static void addParametersIfNeeded(HttpServletRequest request,
            OAuthMessage oAuthMessage) throws IOException {
        if (oAuthMessage.getParameters().isEmpty() 
            && MediaType.APPLICATION_FORM_URLENCODED.equals(oAuthMessage.getBodyType())) {
            String enc = oAuthMessage.getBodyEncoding();
            enc = enc == null ? "UTF-8" : enc;
            String body = FormUtils.readBody(oAuthMessage.getBodyAsStream(), enc);
            MultivaluedMap<String, String> map = new MetadataMap<String, String>();
            FormUtils.populateMapFromString(map, body, enc, true, request);
            for (String key : map.keySet()) {
                oAuthMessage.addParameter(key, map.getFirst(key));
            }
        }
    }
    
    
    public static Response handleException(Exception e, int status) {
        return handleException(e, status, null);
    }

    public static Response handleException(Exception e, int status,
                                           String realm) {
        if (e instanceof OAuthProblemException) {
            OAuthProblemException problem = (OAuthProblemException) e;
            OAuthMessage message = new OAuthMessage(null, null, problem
                    .getParameters().entrySet());
            try {
                return
                        Response.status(status).header("WWW-Authenticate",
                                message.getAuthorizationHeader(realm)).entity(e.getMessage()).build();
            } catch (IOException e1) {
                throw new WebApplicationException(
                        Response.status(status).entity(e.getMessage()).build());
            }
        }
        throw new WebApplicationException(
                Response.status(status).entity(e.getMessage()).build());
    }

    public static List<String> parseParamValue(String paramValue, String defaultValue) 
        throws IOException {
        
        List<String> scopeList = new ArrayList<String>();

        if (!StringUtils.isEmpty(paramValue)) {
            StringTokenizer tokenizer = new StringTokenizer(paramValue, ",");

            while (tokenizer.hasMoreTokens()) {
                String token = tokenizer.nextToken();
                scopeList.add(token);
            }
        }
        if (defaultValue != null) {
            scopeList.add(defaultValue);
        }
        return scopeList;
    }

    
    public static RequestToken handleTokenRejectedException() throws OAuthProblemException {
        OAuthProblemException problemEx = new OAuthProblemException(
                OAuth.Problems.TOKEN_REJECTED);
        problemEx
                .setParameter(OAuthProblemException.HTTP_STATUS_CODE, HttpServletResponse.SC_UNAUTHORIZED);
        throw problemEx;
    }

    public static Object instantiateClass(String className) throws Exception {
        Class<?> clazz = ClassLoaderUtils.loadClass(className, OAuthUtils.class);
        return clazz.newInstance();
    }

    public static synchronized OAuthDataProvider getOAuthDataProvider(
            OAuthDataProvider provider,
            ServletContext servletContext) {
        if (provider != null) {
            return provider;
        }
        return getOAuthDataProvider(servletContext);
    }
    
    public static synchronized OAuthDataProvider getOAuthDataProvider(
            ServletContext servletContext) {
        OAuthDataProvider dataProvider = (OAuthDataProvider) servletContext
                .getAttribute(OAuthConstants.OAUTH_DATA_PROVIDER_INSTANCE_KEY);

        if (dataProvider == null) {
            String dataProviderClassName = servletContext
                    .getInitParameter(OAuthConstants.OAUTH_DATA_PROVIDER_CLASS);

            if (StringUtils.isEmpty(dataProviderClassName)) {
                throw new RuntimeException(
                        "There should be provided [ " + OAuthConstants.OAUTH_DATA_PROVIDER_CLASS
                                + " ] context init param in web.xml");
            }
            
            String oauthValidatorClassName = servletContext
                    .getInitParameter(OAuthConstants.OAUTH_DATA_VALIDATOR_CLASS);

            if (StringUtils.isEmpty(oauthValidatorClassName)) {
                //if no validator was provided fallback to default validator
                oauthValidatorClassName = DefaultOAuthValidator.class.getName();
            }

            try {
                dataProvider = (OAuthDataProvider) OAuthUtils
                        .instantiateClass(dataProviderClassName);
               
                servletContext
                        .setAttribute(OAuthConstants.OAUTH_DATA_PROVIDER_INSTANCE_KEY, dataProvider);
            } catch (Exception e) {
                throw new RuntimeException(
                        "Cannot instantiate OAuth Data Provider class: " + dataProviderClassName, e);
            }
        }

        return dataProvider;
    }
}