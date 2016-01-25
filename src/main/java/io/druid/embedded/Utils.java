/*
 * Copyright 2015 eBay Software Foundation
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

package io.druid.embedded;

import io.druid.collections.StupidPool;
import io.druid.query.IntervalChunkingQueryRunnerDecorator;
import io.druid.query.Query;
import io.druid.query.QueryRunner;
import io.druid.query.QueryToolChest;
import io.druid.query.QueryWatcher;

import java.nio.ByteBuffer;
import java.util.Map;

import com.google.common.base.Supplier;
import com.google.common.util.concurrent.ListenableFuture;
import com.metamx.common.guava.Sequence;
import com.metamx.emitter.core.Event;
import com.metamx.emitter.service.ServiceEmitter;

public class Utils {
	public static final int MAX_TOTAL_BUFFER_SIZE = 1024*1024*1024;
	
	private static class ByteBufferSuplier implements Supplier<ByteBuffer> {
		int capacity;

		public ByteBufferSuplier(int capacity) {
			this.capacity = capacity;
		}

		public ByteBuffer get() {
			return ByteBuffer.allocate(capacity);
		}
	}
	
	public static ServiceEmitter NOOP_SERVICE_EMITTER = new ServiceEmitter(null, null, null) {
		@Override
	    public void emit(Event event) {}
	};

	public static final QueryWatcher NOOP_QUERYWATCHER = new QueryWatcher() {
		@SuppressWarnings("rawtypes")
	    public void registerQuery(Query query, ListenableFuture future) {}
	};
	
	public static StupidPool<ByteBuffer> getBufferPool() {
	    return new StupidPool<ByteBuffer>(new ByteBufferSuplier(MAX_TOTAL_BUFFER_SIZE / 2));
	}

	public static IntervalChunkingQueryRunnerDecorator NoopIntervalChunkingQueryRunnerDecorator() {
		return new IntervalChunkingQueryRunnerDecorator(null, null, null) {
			@Override
			public <T> QueryRunner<T> decorate(final QueryRunner<T> delegate,
					QueryToolChest<T, ? extends Query<T>> toolChest) {
				return new QueryRunner<T>() {
		          public Sequence<T> run(Query<T> query, Map<String, Object> responseContext) {
		            return delegate.run(query, responseContext);
		          }
				};
			}
		};
	}
}
