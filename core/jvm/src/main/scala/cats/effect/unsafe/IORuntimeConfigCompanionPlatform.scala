/*
 * Copyright 2020-2021 Typelevel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cats.effect
package unsafe

abstract class IORuntimeConfigCompanionPlatform { this: IORuntimeConfig.type =>

  protected final val Default: IORuntimeConfig = {
    val cancellationCheckThreshold =
      System.getProperty("cats.effect.cancellation.check.threshold", "512").toInt

    apply(
      cancellationCheckThreshold,
      System
        .getProperty("cats.effect.auto.yield.threshold.multiplier", "2")
        .toInt * cancellationCheckThreshold)
  }
}
