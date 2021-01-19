package com.amplifyframework.api.aws;

import com.amplifyframework.api.ApiException;
import com.amplifyframework.api.aws.sigv4.AppSyncMixedModeSignerInterceptor;

import okhttp3.Interceptor;

public class AppSyncMixedModeSignerInterceptorFactory implements InterceptorFactory{

    private final ApiAuthProviders apiAuthProviders;

    public AppSyncMixedModeSignerInterceptorFactory (ApiAuthProviders apiAuthProviders) {
        this.apiAuthProviders = apiAuthProviders;
    }
    @Override
    public Interceptor create(ApiConfiguration config) throws ApiException {
        return new AppSyncMixedModeSignerInterceptor(config.getAuthorizationType(),
                                                     config.getEndpointType(),
                                                     config.getRegion(),
                                                     config.getApiKey(),
                                                     apiAuthProviders);
    }
}
