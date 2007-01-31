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

package org.apache.cxf.transports.http;

import java.io.OutputStream;

import org.apache.cxf.service.model.EndpointInfo;

public interface QueryHandler {
    
    /**
     * @param URI the target URI
     * @param endpoint the current endpoint for this context (e.g. the endpoint this
     * Destination was activated for). Null if no current endpoint.
     * @return true iff the URI is a recognized WSDL query
     */
    boolean isRecognizedQuery(String uri, EndpointInfo endpoint);

    /**
     * @return the content-type for the response
     */
    String getResponseContentType(String uri);
 
    /**
     * Write query response to output stream
     */ 
    void writeResponse(String queryURI, EndpointInfo endpoint, OutputStream os);
    


}
