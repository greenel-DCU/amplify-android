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

package com.amplifyframework.datastore.storage.sqlite;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import androidx.annotation.NonNull;

import com.amplifyframework.core.Amplify;
import com.amplifyframework.core.model.Model;
import com.amplifyframework.core.model.ModelAssociation;
import com.amplifyframework.core.model.ModelSchema;
import com.amplifyframework.core.model.ModelSchemaRegistry;
import com.amplifyframework.datastore.storage.sqlite.adapter.SQLiteTable;
import com.amplifyframework.logging.Logger;
import com.amplifyframework.util.Empty;
import com.amplifyframework.util.GsonFactory;
import com.amplifyframework.util.Wrap;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class to help traverse a tree of models by relationship.
 */
final class SQLiteModelTree {
    private static final Logger LOG = Amplify.Logging.forNamespace("amplify:aws-datastore");

    private final ModelSchemaRegistry registry;
    private final SQLiteDatabase database;
    private final Gson gson;

    /**
     * Constructs a model family tree traversing utility.
     * @param registry model registry to search schema from
     * @param database SQLite database connection handle
     */
    SQLiteModelTree(ModelSchemaRegistry registry,
                    SQLiteDatabase database) {
        this.registry = registry;
        this.database = database;
        this.gson = GsonFactory.instance();
    }

    /**
     * Returns a map of descendants of a set of models (of same type).
     * A model is a child of its parent if it uses its parent's ID as foreign key.
     * @param root Collection of models to query its descendants of.
     * @return List of models that are descendants of given models. These models will
     *          have the correct model type and ID, but no other field will be populated.
     */
    <T extends Model> List<Model> descendantsOf(Collection<T> root) {
        if (Empty.check(root)) {
            return new ArrayList<>();
        }
        Map<ModelSchema, Set<String>> modelMap = new LinkedHashMap<>();
        ModelSchema rootSchema = registry.getModelSchemaForModelInstance(root.iterator().next());
        Set<String> rootIds = new HashSet<>();
        for (T model : root) {
            rootIds.add(model.getId());
        }
        recurseTree(modelMap, rootSchema, rootIds);

        List<Model> descendants = new ArrayList<>();
        for (Map.Entry<ModelSchema, Set<String>> entry : modelMap.entrySet()) {
            ModelSchema schema = entry.getKey();
            for (String id : entry.getValue()) {
                // Create dummy model instance using just the ID and model type
                String dummyJson = gson.toJson(Collections.singletonMap("id", id));
                Model dummyItem = gson.fromJson(dummyJson, schema.getModelClass());
                descendants.add(dummyItem);
            }
        }
        return descendants;
    }

    private void recurseTree(
            Map<ModelSchema, Set<String>> map,
            ModelSchema modelSchema,
            Collection<String> parentIds
    ) {
        for (ModelAssociation association : modelSchema.getAssociations().values()) {
            switch (association.getName()) {
                case "HasOne":
                case "HasMany":
                    String childModel = association.getAssociatedType(); // model name
                    ModelSchema childSchema = registry.getModelSchemaForModelClass(childModel);
                    SQLiteTable childTable = SQLiteTable.fromSchema(childSchema);
                    String childId = childTable.getPrimaryKey().getName();
                    String parentId = childSchema.getAssociations() // get a map of associations
                            .get(association.getAssociatedName()) // get @BelongsTo association linked to this field
                            .getTargetName(); // get the target field (parent) name

                    // Collect every children one level deeper than current level
                    // SELECT * FROM <CHILD_TABLE> WHERE <PARENT> = <ID_1> OR <PARENT> = <ID_2> OR ...
                    Set<String> childrenIds = new HashSet<>();
                    try (Cursor cursor = queryChildren(childTable.getName(), childId, parentId, parentIds)) {
                        if (cursor != null && cursor.moveToFirst()) {
                            int index = cursor.getColumnIndexOrThrow(childId);
                            do {
                                childrenIds.add(cursor.getString(index));
                            } while (cursor.moveToNext());
                        }
                    } catch (SQLiteException exception) {
                        // Don't cut the search short. Populate rest of the tree.
                        LOG.error("Failed to query children of deleted model(s).", exception);
                    }

                    // Add queried result to the map
                    if (!childrenIds.isEmpty()) {
                        if (!map.containsKey(childSchema)) {
                            map.put(childSchema, childrenIds);
                        } else {
                            map.get(childSchema).addAll(childrenIds);
                        }
                        recurseTree(map, childSchema, childrenIds);
                    }
                    break;
                case "BelongsTo":
                default:
                    // Ignore other relationships
            }
        }
    }

    private Cursor queryChildren(
            @NonNull String childTable,
            @NonNull String childIdField,
            @NonNull String parentIdField,
            @NonNull Collection<String> parentIds
    ) {
        // SELECT <child_id>, <parent_id> FROM <child_table> WHERE <parent_id> IN (<id_1>, <id_2>, ...)
        String queryString = String.valueOf(SqlKeyword.SELECT) +
                SqlKeyword.DELIMITER +
                Wrap.inBackticks(childIdField) +
                SqlKeyword.SEPARATOR +
                Wrap.inBackticks(parentIdField) +
                SqlKeyword.DELIMITER +
                SqlKeyword.FROM +
                SqlKeyword.DELIMITER +
                Wrap.inBackticks(childTable) +
                SqlKeyword.DELIMITER +
                SqlKeyword.WHERE +
                SqlKeyword.DELIMITER +
                Wrap.inBackticks(parentIdField) +
                SqlKeyword.DELIMITER +
                SqlKeyword.IN +
                SqlKeyword.DELIMITER +
                Wrap.inParentheses(parentIds
                    .stream()
                    .map(Wrap::inSingleQuotes)
                    .collect(Collectors.joining(SqlKeyword.SEPARATOR.toString()))) +
                ";";
        return database.rawQuery(queryString, new String[0]);
    }
}
