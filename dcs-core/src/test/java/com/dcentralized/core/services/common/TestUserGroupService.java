/*
 * Copyright (c) 2014-2015 dCentralizedSystems, LLC. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.dcentralized.core.services.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URI;

import com.dcentralized.core.common.BasicReusableHostTestCase;
import com.dcentralized.core.common.Operation;
import com.dcentralized.core.common.UriUtils;
import com.dcentralized.core.services.common.QueryTask.Query;
import com.dcentralized.core.services.common.UserGroupService.UserGroupState;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestUserGroupService extends BasicReusableHostTestCase {
    private URI factoryUri;

    @Before
    public void setUp() {
        this.factoryUri = UriUtils.buildUri(this.host, ServiceUriPaths.CORE_AUTHZ_USER_GROUPS);
    }

    @After
    public void cleanUp() throws Throwable {
        this.host.deleteAllChildServices(this.factoryUri);
    }

    @Test
    public void testFactoryPost() throws Throwable {
        Query query = new Query();
        query.setTermPropertyName("name");
        query.setTermMatchValue("value");
        UserGroupState state = UserGroupState.Builder.create()
                .withQuery(query)
                .build();

        final UserGroupState[] outState = new UserGroupState[1];

        Operation op = Operation.createPost(this.factoryUri)
                .setBody(state)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        this.host.failIteration(e);
                        return;
                    }

                    outState[0] = o.getBody(UserGroupState.class);
                    this.host.completeIteration();
                });

        this.host.testStart(1);
        this.host.send(op);
        this.host.testWait();

        assertEquals(state.query.term.propertyName, outState[0].query.term.propertyName);
        assertEquals(state.query.term.matchValue, outState[0].query.term.matchValue);
    }

    @Test
    public void testFactoryIdempotentPost() throws Throwable {
        Query query = new Query();
        query.setTermPropertyName("name");
        query.setTermMatchValue("value");

        String servicePath = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, "my-user");

        UserGroupState state = UserGroupState.Builder.create()
                .withSelfLink(servicePath)
                .withQuery(query)
                .build();

        UserGroupState responseState = this.host.verifyPost(UserGroupState.class,
                ServiceUriPaths.CORE_AUTHZ_USER_GROUPS,
                state,
                Operation.STATUS_CODE_OK);

        assertEquals(state.query.term.propertyName, responseState.query.term.propertyName);
        assertEquals(state.query.term.matchValue, responseState.query.term.matchValue);

        long initialVersion = responseState.documentVersion;

        // sending same document, this post/put should not persist(increment) the document
        responseState = this.host.verifyPost(UserGroupState.class,
                ServiceUriPaths.CORE_AUTHZ_USER_GROUPS,
                state,
                Operation.STATUS_CODE_OK);

        assertEquals(state.query.term.propertyName, responseState.query.term.propertyName);
        assertEquals(state.query.term.matchValue, responseState.query.term.matchValue);

        UserGroupState getState = this.sender.sendAndWait(Operation.createGet(this.host, servicePath), UserGroupState.class);
        assertEquals("version should not increase", initialVersion, getState.documentVersion);

        // modify state
        state.query.setTermMatchValue("valueModified");

        responseState = this.host.verifyPost(UserGroupState.class,
                ServiceUriPaths.CORE_AUTHZ_USER_GROUPS,
                state,
                Operation.STATUS_CODE_OK);

        assertEquals(state.query.term.propertyName, responseState.query.term.propertyName);
        assertEquals(state.query.term.matchValue, responseState.query.term.matchValue);
        assertTrue("version should increase", initialVersion < responseState.documentVersion);

    }

    @Test
    public void testFactoryPostFailure() throws Throwable {
        UserGroupState state = UserGroupState.Builder.create()
                .withQuery(null)
                .build();

        Operation[] outOp = new Operation[1];
        Throwable[] outEx = new Throwable[1];

        Operation op = Operation.createPost(this.factoryUri)
                .setBody(state)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        outOp[0] = o;
                        outEx[0] = e;
                        this.host.completeIteration();
                        return;
                    }

                    // No exception, fail test
                    this.host.failIteration(new IllegalStateException("expected failure"));
                });

        this.host.testStart(1);
        this.host.send(op);
        this.host.testWait();

        assertEquals(Operation.STATUS_CODE_FAILURE_THRESHOLD, outOp[0].getStatusCode());
        assertEquals("query is required", outEx[0].getMessage());
    }
}
