/*
 * Copyright (C) 2016 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.functional.producers.monitoring;

import com.google.common.util.concurrent.ListenableFuture;
import dagger.functional.producers.monitoring.ThreadQualifiers.EntryPoint;
import dagger.producers.ProductionComponent;
import javax.inject.Singleton;

@Singleton
@ProductionComponent(modules = {ExecutorModule.class, MonitoringModule.class, ThreadModule.class})
interface ThreadMonitoredComponent {
  @EntryPoint
  ListenableFuture<ThreadAccumulator> threadAccumulator();
}
