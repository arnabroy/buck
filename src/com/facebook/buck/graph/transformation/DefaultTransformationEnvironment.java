/*
 * Copyright 2018-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.graph.transformation;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A computation environment that {@link AsyncTransformer} can access. This class provides ability
 * of {@link AsyncTransformer}s to request and execute their dependencies on the engine, without
 * exposing blocking operations.
 */
final class DefaultTransformationEnvironment<ComputeKey, ComputeResult>
    implements TransformationEnvironment<ComputeKey, ComputeResult> {

  private final AsyncTransformationEngine<ComputeKey, ComputeResult> engine;

  /**
   * Package protected constructor so only {@link DefaultAsyncTransformationEngine} can create the
   * environment
   */
  DefaultTransformationEnvironment(AsyncTransformationEngine<ComputeKey, ComputeResult> engine) {
    this.engine = engine;
  }

  @Override
  public final CompletionStage<ComputeResult> evaluate(
      ComputeKey key, Function<ComputeResult, ComputeResult> asyncTransformation) {
    return engine.compute(key).thenApplyAsync(asyncTransformation);
  }

  @Override
  public final CompletionStage<ComputeResult> evaluateAll(
      Iterable<ComputeKey> keys,
      Function<ImmutableMap<ComputeKey, ComputeResult>, ComputeResult> asyncTransformation) {
    return collectAsyncAndRunInternal(engine.computeAll(keys), asyncTransformation);
  }

  @Override
  public final CompletionStage<ComputeResult> collectAsyncAndRun(
      ImmutableMap<ComputeKey, CompletionStage<ComputeResult>> toCollect,
      Function<ImmutableMap<ComputeKey, ComputeResult>, ComputeResult> thenFunc) {
    return collectAsyncAndRunInternal(
        toCollect
            .entrySet()
            .parallelStream()
            .map(
                entry ->
                    Maps.immutableEntry(entry.getKey(), entry.getValue().toCompletableFuture()))
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue)),
        thenFunc);
  }

  private CompletionStage<ComputeResult> collectAsyncAndRunInternal(
      Map<ComputeKey, CompletableFuture<ComputeResult>> toCollect,
      Function<ImmutableMap<ComputeKey, ComputeResult>, ComputeResult> thenFunc) {
    return DefaultAsyncTransformationEngine.collectFutures(toCollect).thenApplyAsync(thenFunc);
  }
}