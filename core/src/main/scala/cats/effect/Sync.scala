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

package cats.effect

import cats.{Defer, Monad, MonadError}
import cats.data.{OptionT, EitherT, ReaderT, Kleisli}
import scala.concurrent.duration.FiniteDuration
import java.time.Instant

trait Sync[F[_]] extends MonadError[F, Throwable] with Clock[F] with Defer[F] {
  def delay[A](thunk: => A): F[A]

  def defer[A](thunk: => F[A]): F[A] =
    flatMap(delay(thunk))(x => x)
}

object Sync {
  def apply[F[_]](implicit F: Sync[F]): F.type = F

  implicit def optionTSync[F[_]: Sync]: Sync[OptionT[F, *]] =
    new OptionTSync[F] {
      override def F: Sync[F] = Sync[F]
    }

  implicit def eitherTSync[F[_]: Sync, E]: Sync[EitherT[F, E, *]] =
    new EitherTSync[F, E] {
      override def F: Sync[F] = Sync[F]
    }

  implicit def kleisliSync[F[_]: Sync, R]: Sync[Kleisli[F, R, *]] =
    new KleisliSync[F, R] {
      override def F: Sync[F] = Sync[F]
    }

  trait OptionTSync[F[_]] extends Sync[OptionT[F, *]] {
    implicit protected def F: Sync[F]

    override def pure[A](x: A): OptionT[F, A] = Monad[OptionT[F, *]].pure(x)

    override def raiseError[A](e: Throwable): OptionT[F, A] =
      MonadError[OptionT[F, *], Throwable].raiseError(e)

    override def handleErrorWith[A](fa: OptionT[F, A])(
        f: Throwable => OptionT[F, A]
    ): OptionT[F, A] =
      MonadError[OptionT[F, *], Throwable].handleErrorWith(fa)(f)

    override def flatMap[A, B](fa: OptionT[F, A])(
        f: A => OptionT[F, B]
    ): OptionT[F, B] = Monad[OptionT[F, *]].flatMap(fa)(f)

    override def tailRecM[A, B](a: A)(
        f: A => OptionT[F, Either[A, B]]
    ): OptionT[F, B] = Monad[OptionT[F, *]].tailRecM(a)(f)

    override def monotonic: OptionT[F, FiniteDuration] =
      OptionT.liftF(F.monotonic)

    override def realTime: OptionT[F, Instant] = OptionT.liftF(F.realTime)

    override def delay[A](thunk: => A): OptionT[F, A] =
      OptionT.liftF(F.delay(thunk))

    override def defer[A](thunk: => OptionT[F, A]): OptionT[F, A] =
      OptionT(F.defer(thunk.value))
  }

  trait EitherTSync[F[_], E] extends Sync[EitherT[F, E, *]] {
    implicit protected def F: Sync[F]

    override def pure[A](x: A): EitherT[F, E, A] =
      Monad[EitherT[F, E, *]].pure(x)

    override def raiseError[A](e: Throwable): EitherT[F, E, A] =
      MonadError[EitherT[F, E, *], Throwable].raiseError(e)

    override def handleErrorWith[A](fa: EitherT[F, E, A])(
        f: Throwable => EitherT[F, E, A]
    ): EitherT[F, E, A] =
      MonadError[EitherT[F, E, *], Throwable].handleErrorWith(fa)(f)

    override def flatMap[A, B](fa: EitherT[F, E, A])(
        f: A => EitherT[F, E, B]
    ): EitherT[F, E, B] = Monad[EitherT[F, E, *]].flatMap(fa)(f)

    override def tailRecM[A, B](a: A)(
        f: A => EitherT[F, E, Either[A, B]]
    ): EitherT[F, E, B] = Monad[EitherT[F, E, *]].tailRecM(a)(f)

    override def monotonic: EitherT[F, E, FiniteDuration] =
      EitherT.liftF(F.monotonic)

    override def realTime: EitherT[F, E, Instant] = EitherT.liftF(F.realTime)

    override def delay[A](thunk: => A): EitherT[F, E, A] =
      EitherT.liftF(F.delay(thunk))

    override def defer[A](thunk: => EitherT[F, E, A]): EitherT[F, E, A] =
      EitherT(F.defer(thunk.value))
  }

  trait KleisliSync[F[_], R] extends Sync[Kleisli[F, R, *]] {
    implicit protected def F: Sync[F]

    override def pure[A](x: A): Kleisli[F, R, A] =
      Monad[Kleisli[F, R, *]].pure(x)

    override def raiseError[A](e: Throwable): Kleisli[F, R, A] =
      MonadError[Kleisli[F, R, *], Throwable].raiseError(e)

    override def handleErrorWith[A](fa: Kleisli[F, R, A])(
        f: Throwable => Kleisli[F, R, A]
    ): Kleisli[F, R, A] =
      MonadError[Kleisli[F, R, *], Throwable].handleErrorWith(fa)(f)

    override def flatMap[A, B](fa: Kleisli[F, R, A])(
        f: A => Kleisli[F, R, B]
    ): Kleisli[F, R, B] = Monad[Kleisli[F, R, *]].flatMap(fa)(f)

    override def tailRecM[A, B](a: A)(
        f: A => Kleisli[F, R, Either[A, B]]
    ): Kleisli[F, R, B] = Monad[Kleisli[F, R, *]].tailRecM(a)(f)

    override def monotonic: Kleisli[F, R, FiniteDuration] =
      Kleisli.liftF(F.monotonic)

    override def realTime: Kleisli[F, R, Instant] = Kleisli.liftF(F.realTime)

    override def delay[A](thunk: => A): Kleisli[F, R, A] =
      Kleisli.liftF(F.delay(thunk))

    override def defer[A](thunk: => Kleisli[F, R, A]): Kleisli[F, R, A] =
      Kleisli { r => F.defer(thunk.run(r)) }
  }
}
