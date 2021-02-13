/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amplifyframework.api.aws.sigv4;

import java.io.IOException;

import okhttp3.Request;

/**
 * Implementations of this interface should be used to sign
 * HTTP requests sent to AppSync.
 */
public interface AWSRequestSigner {

    /**
     * Implementations of this method should take in an instance of the OkHttp Request
     * object and return a new request with the necessary signature information.
     * @param httpRequest The unsigned HTTP request.
     * @return The signed request.
     * @throws IOException When an issue occurs when reading the request body.
     */
    Request sign(Request httpRequest) throws IOException;
}
