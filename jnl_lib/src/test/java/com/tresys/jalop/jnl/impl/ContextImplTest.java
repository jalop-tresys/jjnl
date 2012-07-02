/*
 * Source code in 3rd-party is licensed and owned by their respective
 * copyright holders.
 *
 * All other source code is copyright Tresys Technology and licensed as below.
 *
 * Copyright (c) 2012 Tresys Technology LLC, Columbia, Maryland, USA
 *
 * This software was developed by Tresys Technology LLC
 * with U.S. Government sponsorship.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tresys.jalop.jnl.impl;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.tresys.jalop.jnl.ConnectionHandler;
import com.tresys.jalop.jnl.Publisher;
import com.tresys.jalop.jnl.Session;
import com.tresys.jalop.jnl.Subscriber;

public class ContextImplTest {

    private LinkedList<String> encodings;
    private LinkedList<String> digests;
    private static Field       tlsField;
    private static Field       jalSessionsField;
    private static Field       connectionStateField;

    @BeforeClass
    public static void setUpBeforeClass() throws SecurityException, NoSuchFieldException {
        tlsField = ContextImpl.class.getDeclaredField("tlsRequired");
        tlsField.setAccessible(true);

        jalSessionsField = ContextImpl.class.getDeclaredField("jalSessions");
        jalSessionsField.setAccessible(true);

        connectionStateField = ContextImpl.class.getDeclaredField("connectionState");
        connectionStateField.setAccessible(true);
    }

    @Before
    public void setUp() throws Exception {
        encodings = new LinkedList<String>();
        encodings.push("enc_foo");
        encodings.push("enc_bar");

        digests = new LinkedList<String>();
        digests.push("dgst_foo");
        digests.push("dgst_bar");
    }

    @SuppressWarnings("unchecked")
    private static List<Session> getSessions(ContextImpl c) throws IllegalArgumentException, IllegalAccessException {
        return (List<Session>) jalSessionsField.get(c);
    }

    @Test
    public final void testContextImplConstructorWithoutPublisher(Subscriber subscriber,
            ConnectionHandler connectionHandler) throws IllegalArgumentException, IllegalAccessException {
        ContextImpl c = new ContextImpl(null, subscriber, connectionHandler, 100, 150, false, digests, encodings);
        assertEquals(null, c.getPublisher());
        assertEquals(subscriber, c.getSubscriber());
        assertEquals(connectionHandler, c.getConnectionHandler());
        assertEquals(100, c.getDefaultDigestTimeout());
        assertEquals(150, c.getDefaultPendingDigestMax());
        assertFalse(tlsField.getBoolean(c));

        assertArrayEquals(encodings.toArray(new String[0]),
                          Lists.newArrayList(c.getAllowedXmlEncodings()).toArray(new String[0]));
        assertArrayEquals(digests.toArray(new String[0]),
                          Lists.newArrayList(c.getAllowedMessageDigests()).toArray(new String[0]));
        assertEquals(ContextImpl.ConnectionState.DISCONNECTED, connectionStateField.get(c));
        assertNotNull(getSessions(c));
        assertTrue(getSessions(c).isEmpty());

    }

    @Test
    public final void testContextImplConstructorWorksWithoutSubscriber(Publisher publisher,
            ConnectionHandler connectionHandler) throws IllegalArgumentException, IllegalAccessException {
        ContextImpl c = new ContextImpl(publisher, null, connectionHandler, 100, 150, false, digests, encodings);
        assertEquals(publisher, c.getPublisher());
        assertEquals(null, c.getSubscriber());
        assertEquals(connectionHandler, c.getConnectionHandler());
        assertEquals(100, c.getDefaultDigestTimeout());
        assertEquals(150, c.getDefaultPendingDigestMax());
        assertFalse(tlsField.getBoolean(c));

        assertArrayEquals(encodings.toArray(new String[0]),
                          Lists.newArrayList(c.getAllowedXmlEncodings()).toArray(new String[0]));
        assertArrayEquals(digests.toArray(new String[0]),
                          Lists.newArrayList(c.getAllowedMessageDigests()).toArray(new String[0]));
        assertEquals(ContextImpl.ConnectionState.DISCONNECTED, connectionStateField.get(c));
        assertNotNull(getSessions(c));
        assertTrue(getSessions(c).isEmpty());

    }

    @Test
    public final void testContextImplConstructorWorksWithoutConnectionHandler(Publisher publisher, Subscriber subscriber)
            throws IllegalArgumentException, IllegalAccessException {
        ContextImpl c = new ContextImpl(publisher, subscriber, null, 100, 150, false, digests, encodings);
        assertEquals(publisher, c.getPublisher());
        assertEquals(subscriber, c.getSubscriber());
        assertEquals(null, c.getConnectionHandler());
        assertEquals(100, c.getDefaultDigestTimeout());
        assertEquals(150, c.getDefaultPendingDigestMax());
        assertFalse(tlsField.getBoolean(c));

        assertArrayEquals(encodings.toArray(new String[0]),
                          Lists.newArrayList(c.getAllowedXmlEncodings()).toArray(new String[0]));
        assertArrayEquals(digests.toArray(new String[0]),
                          Lists.newArrayList(c.getAllowedMessageDigests()).toArray(new String[0]));
        assertEquals(ContextImpl.ConnectionState.DISCONNECTED, connectionStateField.get(c));
        assertNotNull(getSessions(c));
        assertTrue(getSessions(c).isEmpty());

    }

    @Test
    public final void testContextImplConstructorWorksWithTlsRequired(Publisher publisher, Subscriber subscriber,
            ConnectionHandler connectionHandler) throws IllegalArgumentException, IllegalAccessException {
        ContextImpl c = new ContextImpl(publisher, subscriber, connectionHandler, 100, 150, true, digests, encodings);
        assertEquals(publisher, c.getPublisher());
        assertEquals(subscriber, c.getSubscriber());
        assertEquals(connectionHandler, c.getConnectionHandler());
        assertEquals(100, c.getDefaultDigestTimeout());
        assertEquals(150, c.getDefaultPendingDigestMax());
        assertTrue(tlsField.getBoolean(c));

        assertArrayEquals(encodings.toArray(new String[0]),
                          Lists.newArrayList(c.getAllowedXmlEncodings()).toArray(new String[0]));
        assertArrayEquals(digests.toArray(new String[0]),
                          Lists.newArrayList(c.getAllowedMessageDigests()).toArray(new String[0]));
        assertEquals(ContextImpl.ConnectionState.DISCONNECTED, connectionStateField.get(c));
        assertNotNull(getSessions(c));
        assertTrue(getSessions(c).isEmpty());

    }

    @Test
    public final void testContextImplConstructorWorksNullDigests(Publisher publisher, Subscriber subscriber,
            ConnectionHandler connectionHandler) throws IllegalArgumentException, IllegalAccessException {
        ContextImpl c = new ContextImpl(publisher, subscriber, connectionHandler, 100, 150, false, null, encodings);
        assertEquals(publisher, c.getPublisher());
        assertEquals(subscriber, c.getSubscriber());
        assertEquals(connectionHandler, c.getConnectionHandler());
        assertEquals(100, c.getDefaultDigestTimeout());
        assertEquals(150, c.getDefaultPendingDigestMax());
        assertFalse(tlsField.getBoolean(c));

        assertArrayEquals(encodings.toArray(new String[0]),
                          Lists.newArrayList(c.getAllowedXmlEncodings()).toArray(new String[0]));
        assertNotNull(c.getAllowedMessageDigests());
        List<String> dgsts = Lists.newArrayList(c.getAllowedMessageDigests());
        assertNotNull(dgsts);
        assertEquals(1, dgsts.size());
        assertEquals("sha256", dgsts.get(0));
        assertEquals(ContextImpl.ConnectionState.DISCONNECTED, connectionStateField.get(c));
        assertNotNull(getSessions(c));
        assertTrue(getSessions(c).isEmpty());

    }

    @Test
    public final void testContextImplConstructorWorksWithEmptyDigests(Publisher publisher, Subscriber subscriber,
            ConnectionHandler connectionHandler) throws IllegalArgumentException, IllegalAccessException {
        digests.clear();
        ContextImpl c = new ContextImpl(publisher, subscriber, connectionHandler, 100, 150, false, digests, encodings);
        assertEquals(publisher, c.getPublisher());
        assertEquals(subscriber, c.getSubscriber());
        assertEquals(connectionHandler, c.getConnectionHandler());
        assertEquals(100, c.getDefaultDigestTimeout());
        assertEquals(150, c.getDefaultPendingDigestMax());
        assertFalse(tlsField.getBoolean(c));

        assertArrayEquals(encodings.toArray(new String[0]),
                          Lists.newArrayList(c.getAllowedXmlEncodings()).toArray(new String[0]));

        assertNotNull(c.getAllowedMessageDigests());
        List<String> dgsts = Lists.newArrayList(c.getAllowedMessageDigests());
        assertEquals(1, dgsts.size());
        assertEquals("sha256", dgsts.get(0));

        assertEquals(ContextImpl.ConnectionState.DISCONNECTED, connectionStateField.get(c));
        assertNotNull(getSessions(c));
        assertTrue(getSessions(c).isEmpty());

    }

    @Test
    public final void testContextImplConstructorWorksWithNullEncodings(Publisher publisher, Subscriber subscriber,
            ConnectionHandler connectionHandler) throws IllegalArgumentException, IllegalAccessException {
        ContextImpl c = new ContextImpl(publisher, subscriber, connectionHandler, 100, 150, false, digests, null);
        assertEquals(publisher, c.getPublisher());
        assertEquals(subscriber, c.getSubscriber());
        assertEquals(connectionHandler, c.getConnectionHandler());
        assertEquals(100, c.getDefaultDigestTimeout());
        assertEquals(150, c.getDefaultPendingDigestMax());
        assertFalse(tlsField.getBoolean(c));

        assertNotNull(c.getAllowedXmlEncodings());
        List<String> encs = Lists.newArrayList(c.getAllowedXmlEncodings());
        assertNotNull(encs);
        assertEquals(1, encs.size());
        assertEquals("none", encs.get(0));
        assertArrayEquals(digests.toArray(new String[0]),
                          Lists.newArrayList(c.getAllowedMessageDigests()).toArray(new String[0]));
        assertEquals(ContextImpl.ConnectionState.DISCONNECTED, connectionStateField.get(c));
        assertNotNull(getSessions(c));
        assertTrue(getSessions(c).isEmpty());

    }

    @Test
    public final void testContextImplConstructorWorksEmptyEncodings(Publisher publisher, Subscriber subscriber,
            ConnectionHandler connectionHandler) throws IllegalArgumentException, IllegalAccessException {
        encodings.clear();
        ContextImpl c = new ContextImpl(publisher, subscriber, connectionHandler, 100, 150, false, digests, encodings);
        assertEquals(publisher, c.getPublisher());
        assertEquals(subscriber, c.getSubscriber());
        assertEquals(connectionHandler, c.getConnectionHandler());
        assertEquals(100, c.getDefaultDigestTimeout());
        assertEquals(150, c.getDefaultPendingDigestMax());
        assertFalse(tlsField.getBoolean(c));

        assertNotNull(c.getAllowedXmlEncodings());
        List<String> encs = Lists.newArrayList(c.getAllowedXmlEncodings());
        assertEquals(1, encs.size());
        assertEquals("none", encs.get(0));

        assertArrayEquals(digests.toArray(new String[0]),
                          Lists.newArrayList(c.getAllowedMessageDigests()).toArray(new String[0]));
        assertEquals(ContextImpl.ConnectionState.DISCONNECTED, connectionStateField.get(c));
        assertNotNull(getSessions(c));
        assertTrue(getSessions(c).isEmpty());

    }

    @Test(expected = IllegalArgumentException.class)
    public final void testContextImplConstructorThrowsExceptionForZeroDigestTimeout(Publisher publisher,
            Subscriber subscriber, ConnectionHandler connectionHandler) {
        ContextImpl c = new ContextImpl(publisher, subscriber, connectionHandler, 0, 150, false, digests, encodings);
    }

    @Test(expected = IllegalArgumentException.class)
    public final void testContextImplConstructorThrowsExceptionForNegativeDigestTimeout(Publisher publisher,
            Subscriber subscriber, ConnectionHandler connectionHandler) {
        ContextImpl c = new ContextImpl(publisher, subscriber, connectionHandler, -1, 150, false, digests, encodings);
    }

    @Test(expected = IllegalArgumentException.class)
    public final void testContextImplConstructorThrowsExceptionForZeroDigestMax(Publisher publisher,
            Subscriber subscriber, ConnectionHandler connectionHandler) {
        ContextImpl c = new ContextImpl(publisher, subscriber, connectionHandler, 100, 0, false, digests, encodings);
    }

    @Test(expected = IllegalArgumentException.class)
    public final void testContextImplConstructorThrowsExceptionForNegativeDigestMax(Publisher publisher,
            Subscriber subscriber, ConnectionHandler connectionHandler) {
        ContextImpl c = new ContextImpl(publisher, subscriber, connectionHandler, 100, -1, false, digests, encodings);
    }

    @Test(expected = IllegalArgumentException.class)
    public final void testContextImplConstructorThrowsExceptionWhenSubscribeAndPublisherAreNull(
            ConnectionHandler connectionHandler) {
        ContextImpl c = new ContextImpl(null, null, connectionHandler, 100, 10, false, digests, encodings);

    }
}
