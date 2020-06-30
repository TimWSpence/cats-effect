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

import scala.concurrent.duration.FiniteDuration
import cats.data._
import cats.kernel.Monoid
import cats.kernel.Semigroup

trait Temporal[F[_], E] extends Concurrent[F, E] with Clock[F] {
  self: Safe[F, E] =>
  // (sleep(n) *> now) <-> now.map(_ + n + d) forSome { val d: Double }
  def sleep(time: FiniteDuration): F[Unit]
}

object Temporal {
  def apply[F[_], E](implicit F: Temporal[F, E]): F.type = F
  def apply[F[_]](implicit F: Temporal[F, _], d: DummyImplicit): F.type = F

  implicit def optionTTemporal[F[_], E](
      implicit T: Temporal[F, E]
  ): Temporal[OptionT[F, *], E] =
    new OptionTTemporal[F, E] {
      override def F: Temporal[F, E] = T
    }

  implicit def eitherTTemporal[F[_], E1, E2](
      implicit T: Temporal[F, E2]
  ): Temporal[EitherT[F, E1, *], E2] = new EitherTTemporal[F, E1, E2] {
    override def F: Temporal[F, E2] = T
  }

  implicit def stateTTemporal[F[_], S, E](
      implicit T: Temporal[F, E]
  ): Temporal[StateT[F, S, *], E] =
    new StateTTemporal[F, S, E] {
      override def F: Temporal[F, E] = T
    }

  implicit def writerTTemporal[F[_], S: Monoid, E](
      implicit T: Temporal[F, E]
  ): Temporal[WriterT[F, S, *], E] =
    new WriterTTemporal[F, S, E] {
      override def S: Monoid[S] = Monoid[S]
      override def F: Temporal[F, E] = ???
    }

  implicit def iorTTemporal[F[_], L : Semigroup, E](implicit T: Temporal[F, E]): Temporal[IorT[F, L, *], E] =
    new IorTTemporal[F, L, E] {
      override def L: Semigroup[L] = Semigroup[L]

      override def F: Temporal[F,E] = T
    }

  implicit def kleisliTemporal[F[_], R, E](implicit T: Temporal[F, E]): Temporal[Kleisli[F, R, *], E] =
    new KleisliTemporal[F, R, E] {
      override def F: Temporal[F,E] = ???
    }

  trait OptionTTemporal[F[_], E]
      extends Temporal[OptionT[F, *], E]
      with Concurrent.OptionTConcurrent[F, E]
      with Clock.OptionTClock[F] {
    implicit override def F: Temporal[F, E]

    override def sleep(time: FiniteDuration): OptionT[F, Unit] =
      OptionT.liftF(F.sleep(time))
  }

  trait EitherTTemporal[F[_], E1, E2]
      extends Temporal[EitherT[F, E1, *], E2]
      with Concurrent.EitherTConcurrent[F, E1, E2]
      with Clock.EitherTClock[F, E1] {
    implicit override def F: Temporal[F, E2]

    override def sleep(time: FiniteDuration): EitherT[F, E1, Unit] =
      EitherT.liftF(F.sleep(time))
  }

  trait StateTTemporal[F[_], S, E]
      extends Temporal[StateT[F, S, *], E]
      with Concurrent.StateTConcurrent[F, S, E]
      with Clock.StateTClock[F, S] {
    implicit override def F: Temporal[F, E]

    override def sleep(time: FiniteDuration): IndexedStateT[F, S, S, Unit] =
      StateT.liftF(F.sleep(time))
  }

  trait WriterTTemporal[F[_], S, E]
      extends Temporal[WriterT[F, S, *], E]
      with Concurrent.WriterTConcurrent[F, S, E]
      with Clock.WriterTClock[F, S] {
    implicit override def F: Temporal[F, E]

    override def sleep(time: FiniteDuration): WriterT[F, S, Unit] =
      WriterT.liftF(F.sleep(time))
  }
  trait IorTTemporal[F[_], L, E]
      extends Temporal[IorT[F, L, *], E]
      with Concurrent.IorTConcurrent[F, L, E]
      with Clock.IorTClock[F, L] {
    implicit override def F: Temporal[F, E]

    override def sleep(time: FiniteDuration): IorT[F, L, Unit] =
      IorT.liftF(F.sleep(time))
  }
  trait KleisliTemporal[F[_], R, E]
      extends Temporal[Kleisli[F, R, *], E]
      with Concurrent.KleisliConcurrent[F, R, E]
      with Clock.KleisliClock[F, R] {
    implicit override def F: Temporal[F, E]

    override def sleep(time: FiniteDuration): Kleisli[F, R, Unit] =
      Kleisli.liftF(F.sleep(time))

  }
}
