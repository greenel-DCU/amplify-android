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

package com.amplifyframework.api.aws;

import com.amplifyframework.api.ApiException;
import com.amplifyframework.api.aws.sigv4.AppSyncMixedModeSignerInterceptor;

import okhttp3.Interceptor;

/**
 * Factory that creates instances of AppSyncMixedModeSignerInterceptor.
 */
public final class AppSyncMixedModeSignerInterceptorFactory implements InterceptorFactory {

    private final ApiAuthProviders apiAuthProviders;

    /**
     * Constructor for AppSyncMixedModeSignerInterceptorFactory.
     * @param apiAuthProviders instance of API auth providers.
     */
    public AppSyncMixedModeSignerInterceptorFactory(ApiAuthProviders apiAuthProviders) {
        this.apiAuthProviders = apiAuthProviders;
    }

    @Override
    public Interceptor create(ApiConfiguration config) throws ApiException {
        return new AppSyncMixedModeSignerInterceptor(config.getAuthorizationType(),
                                                     config.getEndpointType(),
                                                     config.getRegion(),
                                                     apiAuthProviders);
    }
}
