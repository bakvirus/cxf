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
package org.apache.cxf.rs.security.oauth.data;

import java.util.Collections;
import java.util.List;

public class Client {
    private String consumerKey;
    private String secretKey;
    private String applicationURI;
    private String applicationName;
    
    private String loginName;
        
    private List<String> uris = Collections.emptyList();
    private List<String> scopes = Collections.emptyList();

    public Client(String consumerKey, 
                  String secretKey,
                  String applicationName,
                  String applicationURI) {
        this.consumerKey = consumerKey;
        this.secretKey = secretKey;
        this.applicationURI = applicationURI;
        this.applicationName = applicationName;
    }
    
    public Client(String consumerKey, String secretKey) {
        this(consumerKey, secretKey, null, null);
    }

    public String getConsumerKey() {
        return consumerKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }
    
    public String getApplicationURI() {
        return applicationURI;
    }

    public void setApplicationURI(String applicationURI) {
        this.applicationURI = applicationURI;
    }

    public String getLoginName() {
        return loginName == null ? consumerKey : loginName;
    }
    
    public void setLoginName(String name) {
        this.loginName = name;
    }
    
    public List<String> getUris() {
        return uris;
    }
    
    public void setUris(List<String> uris) {
        this.uris = uris;
    }
    
    public List<String> getScopes() {
        return scopes;
    }

    public void setScopes(List<String> scopes) {
        this.scopes = scopes;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Client that = (Client)o;

        if (!consumerKey.equals(that.consumerKey)) {
            return false;
        }
        if (!secretKey.equals(that.secretKey)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = consumerKey.hashCode();
        result = 31 * result + secretKey.hashCode();
        return result;
    }
}
