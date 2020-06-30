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

import cats.Applicative
import cats.data._

import scala.concurrent.duration.FiniteDuration

import java.time.Instant
import cats.kernel.Monoid
import cats.kernel.Semigroup

trait Clock[F[_]] extends Applicative[F] {

  // (monotonic, monotonic).mapN(_ <= _)
  def monotonic: F[FiniteDuration]

  // lawless (unfortunately), but meant to represent current (when sequenced) system time
  def realTime: F[FiniteDuration]
}

object Clock {
  def apply[F[_]](implicit F: Clock[F]): F.type = F

  implicit def optionTClock[F[_]: Clock]: Clock[OptionT[F, *]] =
    new OptionTClock[F] {
      override def F: Clock[F] = Clock[F]
    }

  implicit def eitherTClock[F[_]: Clock, E]: Clock[EitherT[F, E, *]] =
    new EitherTClock[F, E] {
      override def F: Clock[F] = Clock[F]
    }

  implicit def stateTClock[F[_]: Clock, S]: Clock[StateT[F, S, *]] =
    new StateTClock[F, S] {
      override def F: Clock[F] = Clock[F]
    }

  implicit def writerT[F[_]: Clock, S: Monoid]: Clock[WriterT[F, S, *]] =
    new WriterTClock[F, S] {
      override def F: Clock[F] = Clock[F]

      override def S: Monoid[S] = Monoid[S]

    }

  implicit def iorT[F[_]: Clock, L: Semigroup]: Clock[IorT[F, L, *]] =
    new IorTClock[F, L] {
      override def F: Clock[F] = Clock[F]

      override def L: Semigroup[L] = Semigroup[L]
    }

  implicit def kleisliClock[F[_]: Clock, R]: Clock[Kleisli[F, R, *]] =
    new KleisliClock[F, R] {
      override def F: Clock[F] = Clock[F]
    }

  trait OptionTClock[F[_]] extends Clock[OptionT[F, *]] {
    implicit protected def F: Clock[F]

    override def ap[A, B](
        ff: OptionT[F, A => B]
    )(fa: OptionT[F, A]): OptionT[F, B] =
      Applicative[OptionT[F, *]].ap(ff)(fa)

    override def pure[A](x: A): OptionT[F, A] =
      Applicative[OptionT[F, *]].pure(x)

    override def monotonic: OptionT[F, FiniteDuration] =
      OptionT.liftF(F.monotonic)

    override def realTime: OptionT[F, Instant] = OptionT.liftF(F.realTime)
  }

  trait EitherTClock[F[_], E] extends Clock[EitherT[F, E, *]] {
    implicit protected def F: Clock[F]

    override def ap[A, B](
        ff: EitherT[F, E, A => B]
    )(fa: EitherT[F, E, A]): EitherT[F, E, B] =
      Applicative[EitherT[F, E, *]].ap(ff)(fa)

    override def pure[A](x: A): EitherT[F, E, A] =
      Applicative[EitherT[F, E, *]].pure(x)

    override def monotonic: EitherT[F, E, FiniteDuration] =
      EitherT.liftF(F.monotonic)

    override def realTime: EitherT[F, E, Instant] = EitherT.liftF(F.realTime)
  }

  trait StateTClock[F[_], S] extends Clock[StateT[F, S, *]] {
    implicit protected def F: Clock[F]

    override def ap[A, B](
        ff: IndexedStateT[F, S, S, A => B]
    )(fa: IndexedStateT[F, S, S, A]): IndexedStateT[F, S, S, B] =
      Applicative[StateT[F, S, *]].ap(ff)(fa)

    override def pure[A](x: A): IndexedStateT[F, S, S, A] =
      Applicative[StateT[F, S, *]].pure(x)

    override def monotonic: IndexedStateT[F, S, S, FiniteDuration] =
      StateT.liftF(F.monotonic)

    override def realTime: IndexedStateT[F, S, S, Instant] =
      StateT.liftF(F.realTime)
  }

  trait WriterTClock[F[_], S] extends Clock[WriterT[F, S, *]] {
    implicit protected def F: Clock[F]
    implicit protected def S: Monoid[S]

    override def ap[A, B](
        ff: WriterT[F, S, A => B]
    )(fa: WriterT[F, S, A]): WriterT[F, S, B] =
      Applicative[WriterT[F, S, *]].ap(ff)(fa)

    override def pure[A](x: A): WriterT[F, S, A] =
      Applicative[WriterT[F, S, *]].pure(x)

    override def monotonic: WriterT[F, S, FiniteDuration] =
      WriterT.liftF(F.monotonic)

    override def realTime: WriterT[F, S, Instant] = WriterT.liftF(F.realTime)
  }

  trait IorTClock[F[_], L] extends Clock[IorT[F, L, *]] {
    implicit protected def F: Clock[F]
    implicit protected def L: Semigroup[L]

    override def ap[A, B](
        ff: IorT[F, L, A => B]
    )(fa: IorT[F, L, A]): IorT[F, L, B] =
      Applicative[IorT[F, L, *]].ap(ff)(fa)

    override def pure[A](x: A): IorT[F, L, A] =
      Applicative[IorT[F, L, *]].pure(x)

    override def monotonic: IorT[F, L, FiniteDuration] = IorT.liftF(F.monotonic)

    override def realTime: IorT[F, L, Instant] = IorT.liftF(F.realTime)

  }

  trait KleisliClock[F[_], R] extends Clock[Kleisli[F, R, *]] {
    implicit protected def F: Clock[F]

    override def ap[A, B](
        ff: Kleisli[F, R, A => B]
    )(fa: Kleisli[F, R, A]): Kleisli[F, R, B] =
      Applicative[Kleisli[F, R, *]].ap(ff)(fa)

    override def pure[A](x: A): Kleisli[F, R, A] =
      Applicative[Kleisli[F, R, *]].pure(x)

    override def monotonic: Kleisli[F, R, FiniteDuration] =
      Kleisli.liftF(F.monotonic)

    override def realTime: Kleisli[F, R, Instant] = Kleisli.liftF(F.realTime)

  }
}
