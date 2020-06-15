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
import cats.data._

import scala.concurrent.ExecutionContext
import cats.kernel.{Monoid, Semigroup}

trait Async[F[_]] extends Sync[F] with Temporal[F, Throwable] {
  self: Safe[F, Throwable] =>

  // returns an optional cancelation token
  def async[A](k: (Either[Throwable, A] => Unit) => F[Option[F[Unit]]]): F[A]

  def never[A]: F[A] = async(_ => pure(none[F[Unit]]))

  // evalOn(executionContext, ec) <-> pure(ec)
  def evalOn[A](fa: F[A], ec: ExecutionContext): F[A]
  def executionContext: F[ExecutionContext]
}

object Async {
  def apply[F[_]](implicit F: Async[F]): F.type = F

  implicit def optionTAsync[F[_]: Async]: Async[OptionT[F, *]] =
    new OptionTAsync[F] {
      override def F: Async[F] = Async[F]
    }

  implicit def eitherTAsync[F[_]: Async, E]: Async[EitherT[F, E, *]] =
    new EitherTAsync[F, E] {
      override def F: Async[F] = Async[F]
    }

  implicit def stateTAsync[F[_]: Async, S]: Async[StateT[F, S, *]] =
    new StateTAsync[F, S] {
      override def F: Async[F] = Async[F]
    }

  implicit def writerTAsync[F[_]: Async, S: Monoid]: Async[WriterT[F, S, *]] =
    new WriterTAsync[F, S] {
      override def S: Monoid[S] = Monoid[S]
      override def F: Async[F] = Async[F]
    }

  implicit def iorTAsync[F[_]: Async, L: Semigroup]: Async[IorT[F, L, *]] =
    new IorTAsync[F, L] {
      override def L: Semigroup[L] = Semigroup[L]

      override def F: Async[F] = Async[F]
    }

  implicit def kleisliAsync[F[_]: Async, R]: Async[Kleisli[F, R, *]] =
    new KleisliAsync[F, R] {
      override def F: Async[F] = Async[F]
    }

  trait OptionTAsync[F[_]]
      extends Async[OptionT[F, *]]
      with Sync.OptionTSync[F]
      with Temporal.OptionTTemporal[F, Throwable] {
    override implicit def F: Async[F]

    //See Concurrent.cancelable defn in CE 2
    override def async[A](
        k: (
            Either[Throwable, A] => Unit
        ) => OptionT[F, Option[OptionT[F, Unit]]]
    ): OptionT[F, A] = OptionT.liftF(
      F.async { cb: (Either[Throwable, A] => Unit) =>
        k(cb).value.map(o => o.flatten.map(o2 => o2.value.map(_ => ())))
      }
    )

    override def evalOn[A](
        fa: OptionT[F, A],
        ec: ExecutionContext
    ): OptionT[F, A] = OptionT(F.evalOn(fa.value, ec))

    override def executionContext: OptionT[F, ExecutionContext] =
      OptionT.liftF(F.executionContext)
  }

  trait EitherTAsync[F[_], E]
      extends Async[EitherT[F, E, *]]
      with Sync.EitherTSync[F, E]
      with Temporal.EitherTTemporal[F, E, Throwable] {
    override implicit def F: Async[F]

    override def async[A](
        k: (
            Either[Throwable, A] => Unit
        ) => EitherT[F, E, Option[EitherT[F, E, Unit]]]
    ): EitherT[F, E, A] = EitherT.liftF(
      F.async { cb: (Either[Throwable, A] => Unit) =>
        k(cb).value.map {
          case Left(_) => None
          case Right(v) => v.map(_.value.map(_ => ()))
        }
      }
    )

    override def evalOn[A](
        fa: EitherT[F, E, A],
        ec: ExecutionContext
    ): EitherT[F, E, A] = EitherT(F.evalOn(fa.value, ec))

    override def executionContext: EitherT[F, E, ExecutionContext] =
      EitherT.liftF(F.executionContext)
  }

  trait StateTAsync[F[_], S]
      extends Async[StateT[F, S, *]]
      with Sync.StateTSync[F, S]
      with Temporal.StateTTemporal[F, S, Throwable] {
    override implicit def F: Async[F]

    override def async[A](
        k: (
            Either[Throwable, A] => Unit
        ) => StateT[F, S, Option[StateT[F, S, Unit]]]
    ): StateT[F, S, A] = ???

    override def evalOn[A](
        fa: StateT[F, S, A],
        ec: ExecutionContext
    ): StateT[F, S, A] = StateT(r => F.evalOn(fa.run(r), ec))

    override def executionContext: StateT[F, S, ExecutionContext] =
      StateT.liftF(F.executionContext)
  }

  trait WriterTAsync[F[_], S]
      extends Async[WriterT[F, S, *]]
      with Sync.WriterTSync[F, S]
      with Temporal.WriterTTemporal[F, S, Throwable] {
    override implicit def F: Async[F]

    override def async[A](
        k: (
            Either[Throwable, A] => Unit
        ) => WriterT[F, S, Option[WriterT[F, S, Unit]]]
    ): WriterT[F, S, A] = ???

    override def evalOn[A](
        fa: WriterT[F, S, A],
        ec: ExecutionContext
    ): WriterT[F, S, A] = WriterT(F.evalOn(fa.run, ec))

    override def executionContext: WriterT[F, S, ExecutionContext] =
      WriterT.liftF(F.executionContext)
  }

  trait IorTAsync[F[_], L]
      extends Async[IorT[F, L, *]]
      with Sync.IorTSync[F, L]
      with Temporal.IorTTemporal[F, L, Throwable] {
    override implicit def F: Async[F]

    override def async[A](
        k: (
            Either[Throwable, A] => Unit
        ) => IorT[F, L, Option[IorT[F, L, Unit]]]
    ): IorT[F, L, A] = ???

    override def evalOn[A](
        fa: IorT[F, L, A],
        ec: ExecutionContext
    ): IorT[F, L, A] = IorT(F.evalOn(fa.value, ec))

    override def executionContext: IorT[F, L, ExecutionContext] =
      IorT.liftF(F.executionContext)
  }

  trait KleisliAsync[F[_], R]
      extends Async[Kleisli[F, R, *]]
      with Sync.KleisliSync[F, R]
      with Temporal.KleisliTemporal[F, R, Throwable] {
    override implicit def F: Async[F]

    override def async[A](
        k: (
            Either[Throwable, A] => Unit
        ) => Kleisli[F, R, Option[Kleisli[F, R, Unit]]]
    ): Kleisli[F, R, A] = ???

    override def evalOn[A](
        fa: Kleisli[F, R, A],
        ec: ExecutionContext
    ): Kleisli[F, R, A] = Kleisli(r => F.evalOn(fa.run(r), ec))

    override def executionContext: Kleisli[F, R, ExecutionContext] =
      Kleisli.liftF(F.executionContext)
  }
}
