/*
 * Copyright (C) 2022 The Dagger Authors.
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

package dagger.functional.kotlinsrc.basic

import dagger.Lazy
import dagger.MembersInjector
import javax.inject.Provider

/** A simple interface that exercises all forms of injection for a given type. */
interface Injector<T> {
  fun instance(): T
  fun provider(): Provider<T>
  fun lazy(): Lazy<T>
  fun membersInjector(): MembersInjector<T>
  fun injectMembers(t: T)
  fun injectMembersAndReturn(t: T): T
}
