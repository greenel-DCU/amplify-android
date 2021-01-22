/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package com.amplifyframework.api.graphql.model;

import androidx.annotation.NonNull;

import com.amplifyframework.api.ApiAuthorizationType;
import com.amplifyframework.api.aws.AppSyncGraphQLRequestFactory;
import com.amplifyframework.api.aws.AuthorizationType;
import com.amplifyframework.api.graphql.GraphQLRequest;
import com.amplifyframework.api.graphql.PaginatedResult;
import com.amplifyframework.core.model.Model;
import com.amplifyframework.core.model.query.predicate.QueryPredicate;
import com.amplifyframework.core.model.query.predicate.QueryPredicates;

import java.util.Objects;

/**
 * Helper class that provides methods to create {@link GraphQLRequest} for queries
 * from {@link Model} and {@link QueryPredicate}.
 */
public final class ModelQuery {

    /** This class should not be instantiated. */
    private ModelQuery() {}

    /**
     * Creates a {@link GraphQLRequest} that represents a query that expects a single value as a result.
     * The request will be created with the correct correct document based on the model schema and
     * variables based on given {@code modelId}.
     * @param modelType the model class.
     * @param modelId the model identifier.
     * @param <M> the concrete model type.
     * @return a valid {@link GraphQLRequest} instance.
     */
    public static <M extends Model> GraphQLRequest<M> get(
            @NonNull Class<M> modelType,
            @NonNull String modelId
    ) {
        return get(modelType, modelId, null);
    }

    /**
     * Creates a {@link GraphQLRequest} that represents a query that expects a single value as a result.
     * The request will be created with the correct correct document based on the model schema and
     * variables based on given {@code modelId}.
     * @param modelType the model class.
     * @param modelId the model identifier.
     * @param authorizationType the authorization type for the request.
     * @param <M> the concrete model type.
     * @return a valid {@link GraphQLRequest} instance.
     */
    public static <M extends Model> GraphQLRequest<M> get(
        @NonNull Class<M> modelType,
        @NonNull String modelId,
        AuthorizationType authorizationType
    ) {
        return AppSyncGraphQLRequestFactory.buildQuery(modelType, modelId, authorizationType);
    }

    /**
     * Creates a {@link GraphQLRequest} that represents a query that expects multiple values as a result.
     * The request will be created with the correct document based on the model schema and variables
     * for filtering based on the given predicate.
     * @param modelType the model class.
     * @param predicate the predicate for filtering.
     * @param <M> the concrete model type.
     * @return a valid {@link GraphQLRequest} instance.
     */
    public static <M extends Model> GraphQLRequest<PaginatedResult<M>> list(
            @NonNull Class<M> modelType,
            @NonNull QueryPredicate predicate
    ) {
        return list(modelType, predicate, AuthorizationType.NONE);
    }

    /**
     * Creates a {@link GraphQLRequest} that represents a query that expects multiple values as a result.
     * The request will be created with the correct document based on the model schema and variables
     * for filtering based on the given predicate.
     * @param modelType the model class.
     * @param predicate the predicate for filtering.
     * @param <M> the concrete model type.
     * @return a valid {@link GraphQLRequest} instance.
     */
    public static <M extends Model> GraphQLRequest<PaginatedResult<M>> list(
        @NonNull Class<M> modelType,
        @NonNull QueryPredicate predicate,
        ApiAuthorizationType authorizationType
    ) {
        Objects.requireNonNull(modelType);
        Objects.requireNonNull(predicate);
        return AppSyncGraphQLRequestFactory.buildQuery(modelType, predicate, authorizationType);
    }

    /**
     * Creates a {@link GraphQLRequest} that represents a query that expects multiple values as a result.
     * The request will be created with the correct document based on the model schema.
     * @param modelType the model class.
     * @param <M> the concrete model type.
     * @return a valid {@link GraphQLRequest} instance.
     * @see #list(Class, QueryPredicate)
     */
    public static <M extends Model> GraphQLRequest<PaginatedResult<M>> list(@NonNull Class<M> modelType) {
        return list(modelType, (ApiAuthorizationType) null);
    }

    /**
     * Creates a {@link GraphQLRequest} that represents a query that expects multiple values as a result.
     * The request will be created with the correct document based on the model schema.
     * @param <M> the concrete model type.
     * @param modelType the model class.
     * @param authorizationType
     * @return a valid {@link GraphQLRequest} instance.
     * @see #list(Class, QueryPredicate)
     */
    public static <M extends Model> GraphQLRequest<PaginatedResult<M>> list(@NonNull Class<M> modelType,
                                                                            ApiAuthorizationType authorizationType) {
        return list(modelType, QueryPredicates.all(), authorizationType);
    }

    /**
     * Creates a {@link GraphQLRequest} that represents a query that expects multiple values as a result
     * within a certain range (i.e. paginated).
     * 
     * The request will be created with the correct document based on the model schema and variables
     * for filtering based on the given predicate and pagination.
     * 
     * @param modelType the model class.
     * @param predicate the predicate for filtering.
     * @param pagination the pagination settings.
     * @param <M> the concrete model type.
     * @return a valid {@link GraphQLRequest} instance.
     * @see ModelPagination#firstPage()
     */
    public static <M extends Model> GraphQLRequest<PaginatedResult<M>> list(
            @NonNull Class<M> modelType,
            @NonNull QueryPredicate predicate,
            @NonNull ModelPagination pagination
    ) {
        return AppSyncGraphQLRequestFactory.buildPaginatedResultQuery(
                modelType, predicate, pagination.getLimit());
    }

    /**
     * Creates a {@link GraphQLRequest} that represents a query that expects multiple values as a result
     * within a certain range (i.e. paginated).
     *
     * The request will be created with the correct document based on the model schema and variables
     * for pagination based on the given {@link ModelPagination}.
     *
     * @param modelType the model class.
     * @param pagination the pagination settings.
     * @param <M> the concrete model type.
     * @return a valid {@link GraphQLRequest} instance.
     * @see ModelPagination#firstPage()
     */
    public static <M extends Model> GraphQLRequest<PaginatedResult<M>> list(
            @NonNull Class<M> modelType,
            @NonNull ModelPagination pagination
    ) {
        return AppSyncGraphQLRequestFactory.buildPaginatedResultQuery(
                modelType, QueryPredicates.all(), pagination.getLimit());
    }

}
