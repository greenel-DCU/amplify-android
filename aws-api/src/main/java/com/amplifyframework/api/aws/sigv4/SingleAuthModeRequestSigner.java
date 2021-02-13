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

import com.amplifyframework.api.aws.ApiAuthProviders;
import com.amplifyframework.api.aws.AuthorizationType;
import com.amplifyframework.util.Empty;

import com.amazonaws.DefaultRequest;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.http.HttpMethodName;
import com.amazonaws.util.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.Buffer;

/**
 * HTTP request signer for AppSync that signs the request using the auth mode set in the constructor.
 */
public final class SingleAuthModeRequestSigner implements AWSRequestSigner {
    private static final String CONTENT_TYPE = "application/json";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse(CONTENT_TYPE);
    private static final String APP_SYNC_SERVICE_NAME = "appsync";
    private static final String API_GATEWAY_SERVICE_NAME = "apigateway";
    private static final String X_API_KEY = "x-api-key";
    private static final String AUTHORIZATION = "authorization";

    private final AuthorizationType authMode;
    private final ApiAuthProviders providers;
    private final String awsRegion;
    private final String apiKey;
    private final String serviceName;

    /**
     * Constructor that allows consumers to pass the desired auth mode along with
     * other necessary parameters needed to sign HTTP requests to be sent to AppSync.
     * @param authMode The desired auth mode to use when signing the request.
     * @param providers The provider types that produce the token to be added to the request.
     * @param awsRegion The region where the AppSync API resides.
     */
    public SingleAuthModeRequestSigner(AuthorizationType authMode,
                                       String serviceName,
                                       ApiAuthProviders providers,
                                       String apiKey, //HACK: Each API can have a different key, but we only have instance of auth provider.
                                       String awsRegion) {
        this.authMode = authMode;
        this.serviceName = serviceName;
        this.apiKey = apiKey;
        this.providers = providers;
        this.awsRegion = awsRegion;
    }

    @Override
    public Request sign(Request req) throws IOException {
        //Clone the request into a new DefaultRequest object and populate it with credentials
        final DefaultRequest<?> dr;
        dr = new DefaultRequest<>(serviceName);
        //set the endpoint
        dr.setEndpoint(req.url().uri());
        //copy all the headers
        for (String headerName : req.headers().names()) {
            dr.addHeader(headerName, req.header(headerName));
        }
        //set the http method
        dr.setHttpMethod(HttpMethodName.valueOf(req.method()));

        //set the request body
        final byte[] bodyBytes;
        RequestBody body = req.body();
        if (body != null) {
            //write the body to a byte array.
            final Buffer buffer = new Buffer();
            body.writeTo(buffer);
            bodyBytes = IOUtils.toByteArray(buffer.inputStream());
        } else {
            bodyBytes = "".getBytes();
        }
        dr.setContent(new ByteArrayInputStream(bodyBytes));

        //set the query string parameters
        dr.setParameters(splitQuery(req.url().url()));

        //Sign or Decorate request with the required headers
        if (AuthorizationType.AWS_IAM.equals(authMode) && providers.getApiKeyAuthProvider() != null) {
            //get the aws credentials from provider.
            try {
                //Get credentials - This will refresh the credentials if necessary
                AWSCredentials credentials = providers.getAWSCredentialsProvider().getCredentials();
                //sign the request
                new AppSyncV4Signer(this.awsRegion).sign(dr, credentials);

            } catch (Exception error) {
                throw new IOException("Failed to read credentials to sign the request.", error);
            }
        } else if (AuthorizationType.API_KEY.equals(authMode) && apiKey != null) {
            dr.addHeader(X_API_KEY, apiKey);
        } else if (AuthorizationType.AMAZON_COGNITO_USER_POOLS.equals(authMode) &&
                    providers.getCognitoUserPoolsAuthProvider() != null) {
            try {
                dr.addHeader(AUTHORIZATION, providers.getCognitoUserPoolsAuthProvider().getLatestAuthToken());
            } catch (Exception error) {
                throw new IOException("Failed to retrieve Cognito User Pools token.", error);
            }
        } else if (AuthorizationType.OPENID_CONNECT.equals(authMode) && providers.getOidcAuthProvider() != null) {
            try {
                dr.addHeader(AUTHORIZATION, providers.getOidcAuthProvider().getLatestAuthToken());
            } catch (Exception error) {
                throw new IOException("Failed to retrieve OIDC token.", error);
            }
        }
        //Copy the signed/credentialed request back into an OKHTTP Request object.
        Request.Builder okReqBuilder = new Request.Builder();
        //set the headers from default request, since it contains the signed headers as well.
        for (Map.Entry<String, String> e : dr.getHeaders().entrySet()) {
            okReqBuilder.addHeader(e.getKey(), e.getValue());
        }
        //Set the URL and Method
        okReqBuilder.url(req.url());
        final RequestBody requestBody;
        if (req.body() != null && req.body().contentLength() > 0) {
            requestBody = RequestBody.create(bodyBytes, JSON_MEDIA_TYPE);
        } else {
            requestBody = null;
        }
        okReqBuilder.method(req.method(), requestBody);
        return okReqBuilder.build();
    }

    private static Map<String, String> splitQuery(URL url) throws IOException {
        Map<String, String> queryPairs = new LinkedHashMap<>();
        String query = url.getQuery();
        if (Empty.check(query)) {
            return Collections.emptyMap();
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int index = pair.indexOf("=");
            if (index < 0) {
                throw new MalformedURLException("URL query parameters are malformed.");
            }
            queryPairs.put(
                URLDecoder.decode(pair.substring(0, index), "UTF-8"),
                URLDecoder.decode(pair.substring(index + 1), "UTF-8")
            );
        }
        return queryPairs;
    }
}
