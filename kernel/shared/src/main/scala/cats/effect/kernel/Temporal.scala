/*
 * Copyright 2020 Typelevel
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

package cats.effect.kernel

import cats.implicits._

import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeoutException

trait Temporal[F[_], E] extends Concurrent[F, E] with Clock[F] { self: Safe[F, E] =>
  // (sleep(n) *> now) <-> now.map(_ + n + d) forSome { val d: Double }
  def sleep(time: FiniteDuration): F[Unit]
}

object Temporal {
  def apply[F[_], E](implicit F: Temporal[F, E]): F.type = F
  def apply[F[_]](implicit F: Temporal[F, _], d: DummyImplicit): F.type = F

  def timeoutTo[F[_], E, A](fa: F[A], duration: FiniteDuration, fallback: F[A])(
    implicit F: Temporal[F, E]
  ): F[A] =
    F.race(fa, F.sleep(duration)).flatMap {
      case Left(a)  => F.pure(a)
      case Right(_) => fallback
    }

  def timeout[F[_], A](fa: F[A], duration: FiniteDuration)(implicit F: Temporal[F, Throwable]): F[A] = {
    val timeoutException = F.raiseError[A](new TimeoutException(duration.toString))
    timeoutTo(fa, duration, timeoutException)
  }
}
