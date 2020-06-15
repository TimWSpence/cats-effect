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

import cats.~>
import cats.implicits._
import cats.{Monad, MonadError}
import cats.data.{OptionT, EitherT, ReaderT, Kleisli, StateT, WriterT, IorT}
import cats.kernel.{Monoid, Semigroup}
import scala.concurrent.duration.FiniteDuration
import java.time.Instant

trait SyncEffect[F[_]] extends Sync[F] with Bracket[F, Throwable] {
  type Case[A] = Either[Throwable, A]

  def CaseInstance = catsStdInstancesForEither[Throwable]

  def to[G[_]]: PartiallyApplied[G] =
    new PartiallyApplied[G]

  def toK[G[_]](implicit G: Sync[G] with Bracket[G, Throwable]): F ~> G

  final class PartiallyApplied[G[_]] {
    def apply[A](
        fa: F[A]
    )(implicit G: Sync[G] with Bracket[G, Throwable]): G[A] =
      toK[G](G)(fa)
  }
}

object SyncEffect {
  def apply[F[_]](implicit F: SyncEffect[F]): F.type = F

  implicit def optionTSyncEffect[F[_]: SyncEffect]: SyncEffect[OptionT[F, *]] =
    new OptionTSyncEffect[F] {
      override def F: SyncEffect[F] = Sync[F]
    }

  implicit def eitherTSyncEffect[F[_]: SyncEffect, E]
      : SyncEffect[EitherT[F, E, *]] =
    new EitherTSyncEffect[F, E] {
      override def F: SyncEffect[F] = SyncEffect[F]
    }

  implicit def stateTSyncEffect[F[_]: SyncEffect, S]
      : SyncEffect[StateT[F, S, *]] =
    new StateTSyncEffect[F, S] {
      override def F: SyncEffect[F] = SyncEffect[F]
    }

  implicit def writerTSyncEffect[F[_]: SyncEffect, S: Monoid]
      : SyncEffect[WriterT[F, S, *]] =
    new WriterTSyncEffect[F, S] {
      override def F: SyncEffect[F] = SyncEffect[F]
      override def S: Monoid[S] = Monoid[S]
    }

  implicit def iorTSyncEffect[F[_]: SyncEffect, L: Semigroup]
      : SyncEffect[IorT[F, L, *]] =
    new IorTSyncEffect[F, L] {
      override def F: SyncEffect[F] = SyncEffect[F]
      override def L: Semigroup[L] = Semigroup[L]
    }

  implicit def kleisliSyncEffect[F[_]: SyncEffect, R]
      : SyncEffect[Kleisli[F, R, *]] =
    new KleisliSyncEffect[F, R] {
      override def F: SyncEffect[F] = SyncEffect[F]
    }

  trait OptionTSyncEffect[F[_]]
      extends SyncEffect[OptionT[F, *]]
      with Sync.OptionTSync[F] {

    override protected def F: SyncEffect[F]

    override def bracketCase[A, B](acquire: OptionT[F, A])(
        use: A => OptionT[F, B]
    )(release: (A, Either[Throwable, B]) => OptionT[F, Unit]): OptionT[F, B] = ???

    override def toK[G[_]](
        implicit G: Sync[G] with Bracket[G, Throwable]
    ): OptionT[F, *] ~> G = ???
  }

  trait EitherTSyncEffect[F[_], E]
      extends SyncEffect[EitherT[F, E, *]]
      with Sync.EitherTSync[F, E] {

    override protected def F: SyncEffect[F]

    override def bracketCase[A, B](acquire: EitherT[F, E, A])(
        use: A => EitherT[F, E, B]
    )(release: (A, Either[Throwable, B]) => EitherT[F, E, Unit]): EitherT[F, E, B] = ???

    override def toK[G[_]](
        implicit G: Sync[G] with Bracket[G, Throwable]
    ): EitherT[F, E, *] ~> G = ???

  }

  trait StateTSyncEffect[F[_], S]
      extends SyncEffect[StateT[F, S, *]]
      with Sync.StateTSync[F, S] {

    override protected def F: SyncEffect[F]

    override def bracketCase[A, B](acquire: StateT[F, S, A])(
        use: A => StateT[F, S, B]
    )(release: (A, Either[Throwable, B]) => StateT[F, S, Unit]): StateT[F, S, B] = ???

    override def toK[G[_]](
        implicit G: Sync[G] with Bracket[G, Throwable]
    ): StateT[F, S, *] ~> G = ???
  }

  trait WriterTSyncEffect[F[_], S]
      extends SyncEffect[WriterT[F, S, *]]
      with Sync.WriterTSync[F, S] {

    override protected def F: SyncEffect[F]

    override def bracketCase[A, B](acquire: WriterT[F, S, A])(
        use: A => WriterT[F, S, B]
    )(release: (A, Either[Throwable, B]) => WriterT[F, S, Unit]): WriterT[F, S, B] = ???

    override def toK[G[_]](
        implicit G: Sync[G] with Bracket[G, Throwable]
    ): WriterT[F, S, *] ~> G = ???
  }

  trait IorTSyncEffect[F[_], L]
      extends SyncEffect[IorT[F, L, *]]
      with Sync.IorTSync[F, L] {

    override protected def F: SyncEffect[F]

    override def bracketCase[A, B](acquire: IorT[F, L, A])(
        use: A => IorT[F, L, B]
    )(release: (A, Either[Throwable, B]) => IorT[F, L, Unit]): IorT[F, L, B] = ???

    override def toK[G[_]](
        implicit G: Sync[G] with Bracket[G, Throwable]
    ): IorT[F, L, *] ~> G = ???
  }

  trait KleisliSyncEffect[F[_], R]
      extends SyncEffect[Kleisli[F, R, *]]
      with Sync.KleisliSync[F, R] {
    override protected def F: SyncEffect[F]

    override def bracketCase[A, B](acquire: Kleisli[F, R, A])(
        use: A => Kleisli[F, R, B]
    )(release: (A, Either[Throwable, B]) => Kleisli[F, R, Unit]): Kleisli[F, R, B] = ???

    override def toK[G[_]](
        implicit G: Sync[G] with Bracket[G, Throwable]
    ): Kleisli[F, R, *] ~> G = ???
  }
}
