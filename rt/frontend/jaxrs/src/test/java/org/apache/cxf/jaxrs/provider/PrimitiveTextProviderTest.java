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

package org.apache.cxf.jaxrs.provider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.annotation.Annotation;
import java.util.Arrays;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;

import org.apache.cxf.jaxrs.impl.MetadataMap;

import org.junit.Assert;
import org.junit.Test;

public class PrimitiveTextProviderTest extends Assert {
    
    @Test
    public void testIsWriteable() {
        MessageBodyWriter<Object> p = new PrimitiveTextProvider<Object>();
        assertTrue(p.isWriteable(byte.class, null, null, null)
                   && p.isWriteable(Byte.class, null, null, null)
                   && p.isWriteable(boolean.class, null, null, null)
                   && p.isWriteable(Boolean.class, null, null, null));
    }
    
    @Test
    public void testIsReadable() {
        MessageBodyReader<Object> p = new PrimitiveTextProvider<Object>();
        assertTrue(p.isReadable(byte.class, null, null, null)
                   && p.isReadable(Byte.class, null, null, null)
                   && p.isReadable(boolean.class, null, null, null)
                   && p.isReadable(Boolean.class, null, null, null));
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void testReadByte() throws Exception {
        MessageBodyReader<?> p = new PrimitiveTextProvider<Object>();
        
        @SuppressWarnings("rawtypes")
        Byte valueRead = (Byte)p.readFrom((Class)byte.class, 
                                          null, 
                                          null, 
                                          null, 
                                          null, 
                                          new ByteArrayInputStream("1".getBytes()));
        assertEquals(1, valueRead.byteValue());
        
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testReadBoolean() throws Exception {
        MessageBodyReader p = new PrimitiveTextProvider();
        
        boolean valueRead = (Boolean)p.readFrom(boolean.class, 
                                          null, 
                                          null, 
                                          null, 
                                          null, 
                                          new ByteArrayInputStream("true".getBytes()));
        assertTrue(valueRead);
        
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testWriteBoolean() throws Exception {
        MessageBodyWriter p = new PrimitiveTextProvider();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        p.writeTo(Boolean.TRUE, null, null, null, MediaType.TEXT_PLAIN_TYPE, null, os);
        assertTrue(Arrays.equals(new String("true").getBytes(), os.toByteArray()));
        
        os = new ByteArrayOutputStream();
        
        final boolean value = true;
        p.writeTo(value, null, null, null, MediaType.TEXT_PLAIN_TYPE, null, os);
        assertTrue(Arrays.equals(new String("true").getBytes(), os.toByteArray()));
    }
    
    
    @Test
    public void testWriteStringISO() throws Exception {
        MessageBodyWriter<String> p = new PrimitiveTextProvider<String>();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        MultivaluedMap<String, Object> headers = new MetadataMap<String, Object>();
        
        String eWithAcute = "\u00E9";
        String helloStringUTF16 = "Hello, my name is F" + eWithAcute + "lix Agn" + eWithAcute + "s";
        
        p.writeTo(helloStringUTF16, 
                  String.class, String.class, null, MediaType.valueOf("text/plain;charset=ISO-8859-1"),
                  headers, os);
        
        byte[] iso88591bytes = helloStringUTF16.getBytes("ISO-8859-1");
        String helloStringISO88591 = new String(iso88591bytes, "ISO-8859-1");
        
        assertEquals(helloStringISO88591, os.toString("ISO-8859-1")); 
    }
    
    @Test
    public void testReadChineeseChars() throws Exception {
        String s = "中文";
        
        MessageBodyReader<String> p = new PrimitiveTextProvider<String>();
        
        String value = (String)p.readFrom(String.class, null,
                new Annotation[]{}, 
                MediaType.valueOf(MediaType.APPLICATION_XML + ";charset=UTF-8"), null, 
                new ByteArrayInputStream(s.getBytes("UTF-8")));
        assertEquals(value, value);
    }    
}
