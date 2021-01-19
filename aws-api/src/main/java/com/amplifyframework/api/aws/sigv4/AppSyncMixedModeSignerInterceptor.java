package com.amplifyframework.api.aws.sigv4;

import androidx.annotation.NonNull;

import com.amazonaws.DefaultRequest;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.http.HttpMethodName;
import com.amazonaws.util.IOUtils;
import com.amplifyframework.api.ApiException;
import com.amplifyframework.api.aws.ApiAuthProviders;
import com.amplifyframework.api.aws.AuthorizationType;
import com.amplifyframework.api.aws.EndpointType;
import com.amplifyframework.api.graphql.GraphQLResponse;
import com.amplifyframework.util.Empty;
import com.amplifyframework.util.GsonFactory;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

public class AppSyncMixedModeSignerInterceptor implements Interceptor {
    // TODO: Move these to a central location
    private static final String CONTENT_TYPE = "application/json";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse(CONTENT_TYPE);
    private static final String APP_SYNC_SERVICE_NAME = "appsync";
    private static final String API_GATEWAY_SERVICE_NAME = "apigateway";
    private static final String X_API_KEY = "x-api-key";
    private static final String AUTHORIZATION = "authorization";
    public static final String AMPLIFY_AUTH_MODE_OVERRIDE_HEADER = "Amplify-AuthMode-Override";

    private final AuthorizationType defaultAuthMode;
    private final ApiAuthProviders apiAuthProviders;
    private final EndpointType endpointType;
    private final String awsRegion;
    private final String apiKey;

    public AppSyncMixedModeSignerInterceptor(AuthorizationType defaultAuthMode,
                                             EndpointType endpointType,
                                             String awsRegion,
                                             String apiKey,
                                             ApiAuthProviders apiAuthProviders) {
        this.defaultAuthMode = defaultAuthMode;
        this.awsRegion = awsRegion;
        this.endpointType = endpointType;
        this.apiAuthProviders = apiAuthProviders;
        this.apiKey = apiKey;
    }

    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        Request req = chain.request();
        AuthorizationType reqAuthMode = defaultAuthMode;
        // TODO: Derive auth mode from incoming request via header.
        String authModeOverride = req.header(AMPLIFY_AUTH_MODE_OVERRIDE_HEADER);
        if (authModeOverride != null) {
            reqAuthMode = AuthorizationType.from(authModeOverride);
        }

        //Clone the request into a new DefaultRequest object and populate it with credentials
        final DefaultRequest<?> dr;
        if (endpointType == EndpointType.GRAPHQL) {
            dr = new DefaultRequest<>(APP_SYNC_SERVICE_NAME);
        } else {
            dr = new DefaultRequest<>(API_GATEWAY_SERVICE_NAME);
        }
        //set the endpoint
        dr.setEndpoint(req.url().uri());
        //copy all the headers
        for (String headerName : req.headers().names()) {
            if (!AMPLIFY_AUTH_MODE_OVERRIDE_HEADER.equals(headerName)){
                dr.addHeader(headerName, req.header(headerName));
            }
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
        // Remove that header before sending it
        if (AuthorizationType.AWS_IAM.equals(reqAuthMode)) {
            if (apiAuthProviders.getAWSCredentialsProvider() == null) {
                return buildErrorResponse(req, 400, "Unable to sign request. No AWS credentials provider configured.");
            }
            //get the aws credentials from provider.
            try {
                //Get credentials - This will refresh the credentials if necessary
                AWSCredentials credentials = apiAuthProviders.getAWSCredentialsProvider().getCredentials();
                //sign the request
                if (endpointType == EndpointType.GRAPHQL) {
                    new AppSyncV4Signer(this.awsRegion).sign(dr, credentials);
                } else {
                    new ApiGatewayIamSigner(this.awsRegion).sign(dr, credentials);
                }
            } catch (Exception error) {
                throw new IOException("Failed to read credentials to sign the request.", error);
            }
        } else if (AuthorizationType.AMAZON_COGNITO_USER_POOLS.equals(reqAuthMode)) {
            if (apiAuthProviders.getCognitoUserPoolsAuthProvider() == null) {
                return buildErrorResponse(req, 400, "Unable to sign request. No Cognito User Pools provider configured.");
            }
            try {
                dr.addHeader(AUTHORIZATION, apiAuthProviders.getCognitoUserPoolsAuthProvider().getLatestAuthToken());
            } catch (Exception error) {
                throw new IOException("Failed to retrieve Cognito User Pools token.", error);
            }
        } else if (AuthorizationType.OPENID_CONNECT.equals(reqAuthMode)) {
            if (apiAuthProviders.getOidcAuthProvider() == null) {
                return buildErrorResponse(req, 400, "Unable to sign request. No OIDC provider configured.");
            }
            try {
                dr.addHeader(AUTHORIZATION, apiAuthProviders.getOidcAuthProvider().getLatestAuthToken());
            } catch (Exception error) {
                throw new IOException("Failed to retrieve OIDC token.", error);
            }
        } else if (AuthorizationType.API_KEY.equals(reqAuthMode)) {
            if (apiAuthProviders.getApiKeyAuthProvider() == null && apiKey == null) {
                return buildErrorResponse(req, 400, "Unable to sign request. No API key provider configured.");
            }
            dr.addHeader(X_API_KEY, apiKey != null ? apiKey : apiAuthProviders.getApiKeyAuthProvider().getAPIKey());
        }

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

        //continue with chain.
        return chain.proceed(okReqBuilder.build());
    }

    private static Response buildErrorResponse(Request request, int httpCode, String errorMessage) {
        GraphQLResponse.Error error = new GraphQLResponse.Error(errorMessage, Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
        return new Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .message(errorMessage)
            .code(httpCode)
            .body(ResponseBody
                      .create("{\"errors\":["+ GsonFactory.instance().toJson(error) + "]}",
                              MediaType.get("application/json")))
            .build();
    }

    // TODO: Move this to a separate class
    // Extracts query string parameters from a URL.
    // Source: https://stackoverflow.com/questions/13592236/parse-a-uri-string-into-name-value-collection
    @NonNull
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
