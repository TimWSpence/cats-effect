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

import cats.{ApplicativeError, MonadError}
import cats.data._
import cats.implicits._
import cats.{Functor, Monoid, Semigroup, Semigroupal}

// represents the type Bracket | Region
sealed trait Safe[F[_], E] extends MonadError[F, E] {
  // inverts the contravariance, allowing a lawful bracket without discussing cancelation until Concurrent
  type Case[A]

  implicit def CaseInstance: ApplicativeError[Case, E]
}

trait Bracket[F[_], E] extends Safe[F, E] {

  def bracketCase[A, B](acquire: F[A])(use: A => F[B])(release: (A, Case[B]) => F[Unit]): F[B]

  def bracket[A, B](acquire: F[A])(use: A => F[B])(release: A => F[Unit]): F[B] =
    bracketCase(acquire)(use)((a, _) => release(a))

  def onCase[A](fa: F[A])(pf: PartialFunction[Case[A], F[Unit]]): F[A] =
    bracketCase(unit)(_ => fa)((_, c) => pf.lift(c).getOrElse(unit))
}

object Bracket {
  type Aux[F[_], E, Case0[_]] = Bracket[F, E] { type Case[A] = Case0[A] }
  type Aux2[F[_], E, Case0[_, _]] = Bracket[F, E] { type Case[A] = Case0[E, A] }

  def apply[F[_], E](implicit F: Bracket[F, E]): F.type = F
  def apply[F[_]](implicit F: Bracket[F, _], d: DummyImplicit): F.type = F

  implicit def bracketForEither[E]: Bracket.Aux2[Either[E, *], E, Either] =
    new Bracket[Either[E, *], E] {
      private[this] val delegate = catsStdInstancesForEither[E]

      type Case[A] = Either[E, A]

      def CaseInstance = this

      // export delegate.{pure, handleErrorWith, raiseError, flatMap, tailRecM}

      def pure[A](x: A): Either[E, A] =
        delegate.pure(x)

      def handleErrorWith[A](fa: Either[E, A])(f: E => Either[E, A]): Either[E, A] =
        delegate.handleErrorWith(fa)(f)

      def raiseError[A](e: E): Either[E, A] =
        delegate.raiseError(e)

      def bracketCase[A, B](
        acquire: Either[E, A]
      )(use: A => Either[E, B])(release: (A, Either[E, B]) => Either[E, Unit]): Either[E, B] =
        acquire.flatMap { a =>
          val result: Either[E, B] = use(a)
          productR(attempt(release(a, result)))(result)
        }

      def flatMap[A, B](fa: Either[E, A])(f: A => Either[E, B]): Either[E, B] =
        delegate.flatMap(fa)(f)

      def tailRecM[A, B](a: A)(f: A => Either[E, Either[A, B]]): Either[E, B] =
        delegate.tailRecM(a)(f)
    }

  implicit def bracketForOptionT[F[_], E](
    implicit F: Bracket[F, E]
  ): Bracket.Aux[OptionT[F, *], E, OptionT[F.Case, *]] =
    new Bracket[OptionT[F, *], E] {

      private[this] val delegate = OptionT.catsDataMonadErrorForOptionT[F, E]

      type Case[A] = OptionT[F.Case, A]

      // TODO put this into cats-core
      def CaseInstance: ApplicativeError[Case, E] = new ApplicativeError[Case, E] {

        def pure[A](x: A): OptionT[F.Case, A] =
          OptionT.some[F.Case](x)(F.CaseInstance)

        def handleErrorWith[A](fa: OptionT[F.Case, A])(f: E => OptionT[F.Case, A]): OptionT[F.Case, A] =
          OptionT(F.CaseInstance.handleErrorWith(fa.value)(f.andThen(_.value)))

        def raiseError[A](e: E): OptionT[F.Case, A] =
          OptionT.liftF(F.CaseInstance.raiseError[A](e))(F.CaseInstance)

        def ap[A, B](ff: OptionT[F.Case, A => B])(fa: OptionT[F.Case, A]): OptionT[F.Case, B] =
          OptionT {
            F.CaseInstance.map(F.CaseInstance.product(ff.value, fa.value)) {
              case (optfab, opta) => (optfab, opta).mapN(_(_))
            }
          }
      }

      def pure[A](x: A): OptionT[F, A] =
        delegate.pure(x)

      def handleErrorWith[A](fa: OptionT[F, A])(f: E => OptionT[F, A]): OptionT[F, A] =
        delegate.handleErrorWith(fa)(f)

      def raiseError[A](e: E): OptionT[F, A] =
        delegate.raiseError(e)

      def bracketCase[A, B](
        acquire: OptionT[F, A]
      )(use: A => OptionT[F, B])(release: (A, Case[B]) => OptionT[F, Unit]): OptionT[F, B] =
        OptionT {
          F.bracketCase(acquire.value)((optA: Option[A]) => optA.flatTraverse(a => use(a).value)) {
            (optA: Option[A], resultOpt: F.Case[Option[B]]) =>
              val resultsF = optA.flatTraverse { a =>
                release(a, OptionT(resultOpt)).value
              }

              resultsF.void
          }
        }

      def flatMap[A, B](fa: OptionT[F, A])(f: A => OptionT[F, B]): OptionT[F, B] =
        delegate.flatMap(fa)(f)

      def tailRecM[A, B](a: A)(f: A => OptionT[F, Either[A, B]]): OptionT[F, B] =
        delegate.tailRecM(a)(f)
    }

  implicit def bracketForEitherT[F[_], E0, E](
    implicit F: Bracket[F, E]
  ): Bracket.Aux[EitherT[F, E0, *], E, EitherT[F.Case, E0, *]] =
    new Bracket[EitherT[F, E0, *], E] {

      private[this] val delegate = EitherT.catsDataMonadErrorFForEitherT[F, E, E0]

      type Case[A] = EitherT[F.Case, E0, A]

      // TODO put this into cats-core
      def CaseInstance: ApplicativeError[Case, E] = new ApplicativeError[Case, E] {

        def pure[A](x: A): EitherT[F.Case, E0, A] = EitherT.pure[F.Case, E0](x)(F.CaseInstance)

        def handleErrorWith[A](fa: EitherT[F.Case, E0, A])(f: E => EitherT[F.Case, E0, A]): EitherT[F.Case, E0, A] =
          EitherT(F.CaseInstance.handleErrorWith(fa.value)(f.andThen(_.value)))

        def raiseError[A](e: E): EitherT[F.Case, E0, A] =
          EitherT.liftF(F.CaseInstance.raiseError[A](e))(F.CaseInstance)

        def ap[A, B](ff: EitherT[F.Case, E0, A => B])(fa: EitherT[F.Case, E0, A]): EitherT[F.Case, E0, B] = {
          implicit val eitherInstances: Functor[Either[E0, *]] = catsStdInstancesForEither[E0]

          EitherT {
            F.CaseInstance.map(F.CaseInstance.product(ff.value, fa.value)) {
              case (optfab, opta) =>
                //Sadly the bracketForEither is also in scope here so we have ambiguous implicits
                (optfab, opta).mapN(_(_))(catsStdInstancesForEither[E0], catsStdInstancesForEither[E0])
            }
          }
        }
      }

      def pure[A](x: A): EitherT[F, E0, A] =
        delegate.pure(x)

      def handleErrorWith[A](fa: EitherT[F, E0, A])(f: E => EitherT[F, E0, A]): EitherT[F, E0, A] =
        delegate.handleErrorWith(fa)(f)

      def raiseError[A](e: E): EitherT[F, E0, A] =
        delegate.raiseError(e)

      def bracketCase[A, B](acquire: EitherT[F, E0, A])(use: A => EitherT[F, E0, B])(
        release: (A, Case[B]) => EitherT[F, E0, Unit]
      ): EitherT[F, E0, B] =
        EitherT {
          F.bracketCase(acquire.value)((optA: Either[E0, A]) =>
            optA.flatTraverse(a => use(a).value)(F, catsStdInstancesForEither[E0])
          ) { (optA: Either[E0, A], resultOpt: F.Case[Either[E0, B]]) =>
            val resultsF = optA.flatTraverse { a =>
              release(a, EitherT(resultOpt)).value
            }(F, catsStdInstancesForEither[E0])

            resultsF.void
          }
        }

      def flatMap[A, B](fa: EitherT[F, E0, A])(f: A => EitherT[F, E0, B]): EitherT[F, E0, B] =
        delegate.flatMap(fa)(f)

      def tailRecM[A, B](a: A)(f: A => EitherT[F, E0, Either[A, B]]): EitherT[F, E0, B] =
        delegate.tailRecM(a)(f)
    }

  implicit def bracketForStateT[F[_], S, E](
    implicit F: Bracket[F, E]
  ): Bracket.Aux[StateT[F, S, *], E, StateT[F.Case, S, *]] =
    new Bracket[StateT[F, S, *], E] {

      private[this] val delegate = IndexedStateT.catsDataMonadErrorForIndexedStateT[F, S, E]

      type Case[A] = StateT[F.Case, S, A]

      // TODO put this into cats-core
      def CaseInstance: ApplicativeError[Case, E] = new ApplicativeError[Case, E] {

        def pure[A](x: A): StateT[F.Case, S, A] =
          StateT.pure[F.Case, S, A](x)(F.CaseInstance)

        def handleErrorWith[A](fa: StateT[F.Case, S, A])(f: E => StateT[F.Case, S, A]): StateT[F.Case, S, A] = ???
        // StateT(s => F.CaseInstance.ap(fa.runF)(F.CaseInstance.pure(s)))
        // StateT(F.CaseInstance.handleErrorWith(fa.value)(f.andThen(_.value)))

        def raiseError[A](e: E): StateT[F.Case, S, A] =
          StateT.liftF(F.CaseInstance.raiseError[A](e))(F.CaseInstance)

        def ap[A, B](ff: StateT[F.Case, S, A => B])(fa: StateT[F.Case, S, A]): StateT[F.Case, S, B] = ???
        // OptionT {
        //   F.CaseInstance.map(F.CaseInstance.product(ff.value, fa.value)) {
        //     case (optfab, opta) => (optfab, opta).mapN(_(_))
        //   }
        // }
      }

      def pure[A](x: A): StateT[F, S, A] =
        delegate.pure(x)

      def handleErrorWith[A](fa: StateT[F, S, A])(f: E => StateT[F, S, A]): StateT[F, S, A] =
        delegate.handleErrorWith(fa)(f)

      def raiseError[A](e: E): StateT[F, S, A] =
        delegate.raiseError(e)

      def bracketCase[A, B](
        acquire: StateT[F, S, A]
      )(use: A => StateT[F, S, B])(release: (A, Case[B]) => StateT[F, S, Unit]): StateT[F, S, B] = ???
      // OptionT {
      //   F.bracketCase(acquire.value)((optA: Option[A]) => optA.flatTraverse(a => use(a).value)) {
      //     (optA: Option[A], resultOpt: F.Case[Option[B]]) =>
      //       val resultsF = optA.flatTraverse { a =>
      //         release(a, OptionT(resultOpt)).value
      //       }

      //       resultsF.void
      //   }
      // }

      def flatMap[A, B](fa: StateT[F, S, A])(f: A => StateT[F, S, B]): StateT[F, S, B] =
        delegate.flatMap(fa)(f)

      def tailRecM[A, B](a: A)(f: A => StateT[F, S, Either[A, B]]): StateT[F, S, B] =
        delegate.tailRecM(a)(f)
    }

  implicit def bracketForWriterT[F[_], S, E](
    implicit F: Bracket[F, E],
    S: Monoid[S]
  ): Bracket.Aux[WriterT[F, S, *], E, WriterT[F.Case, S, *]] =
    new Bracket[WriterT[F, S, *], E] {

      private[this] val delegate = WriterT.catsDataMonadErrorForWriterT[F, S, E]

      type Case[A] = WriterT[F.Case, S, A]

      // TODO put this into cats-core
      def CaseInstance: ApplicativeError[Case, E] = new ApplicativeError[Case, E] {

        def pure[A](x: A): WriterT[F.Case, S, A] =
          WriterT(F.CaseInstance.pure((S.empty, x)))

        def handleErrorWith[A](fa: WriterT[F.Case, S, A])(f: E => WriterT[F.Case, S, A]): WriterT[F.Case, S, A] =
          WriterT(F.CaseInstance.handleErrorWith(fa.run)(f.andThen(_.run)))

        def raiseError[A](e: E): WriterT[F.Case, S, A] =
          WriterT.liftF(F.CaseInstance.raiseError[A](e))(S, F.CaseInstance)

        def ap[A, B](ff: WriterT[F.Case, S, A => B])(fa: WriterT[F.Case, S, A]): WriterT[F.Case, S, B] =
          WriterT {
            F.CaseInstance.map(F.CaseInstance.product(ff.run, fa.run)) {
              case (fab, fa) => (fab, fa).mapN(_(_))
            }
          }
      }

      def pure[A](x: A): WriterT[F, S, A] =
        delegate.pure(x)

      def handleErrorWith[A](fa: WriterT[F, S, A])(f: E => WriterT[F, S, A]): WriterT[F, S, A] =
        delegate.handleErrorWith(fa)(f)

      def raiseError[A](e: E): WriterT[F, S, A] =
        delegate.raiseError(e)

      def bracketCase[A, B](
        acquire: WriterT[F, S, A]
      )(use: A => WriterT[F, S, B])(release: (A, Case[B]) => WriterT[F, S, Unit]): WriterT[F, S, B] =
        WriterT {
          F.bracketCase(acquire.run)((optA: (S, A)) => optA.flatTraverse(a => use(a).run)) {
            (optA: (S, A), resultOpt: F.Case[(S, B)]) =>
              val resultsF = optA.flatTraverse { a =>
                release(a, WriterT(resultOpt)).run
              }

              resultsF.void
          }
        }

      def flatMap[A, B](fa: WriterT[F, S, A])(f: A => WriterT[F, S, B]): WriterT[F, S, B] =
        delegate.flatMap(fa)(f)

      def tailRecM[A, B](a: A)(f: A => WriterT[F, S, Either[A, B]]): WriterT[F, S, B] =
        delegate.tailRecM(a)(f)
    }

  implicit def bracketForIorT[F[_], L, E](
    implicit F: Bracket[F, E],
    L: Semigroup[L]
  ): Bracket.Aux[IorT[F, L, *], E, IorT[F.Case, L, *]] =
    new Bracket[IorT[F, L, *], E] {

      private[this] val delegate = IorT.catsDataMonadErrorFForIorT[F, L, E]

      type Case[A] = IorT[F.Case, L, A]

      // TODO put this into cats-core
      def CaseInstance: ApplicativeError[Case, E] = new ApplicativeError[Case, E] {

        def pure[A](x: A): IorT[F.Case, L, A] = IorT.pure[F.Case, L](x)(F.CaseInstance)

        def handleErrorWith[A](fa: IorT[F.Case, L, A])(f: E => IorT[F.Case, L, A]): IorT[F.Case, L, A] =
          IorT(F.CaseInstance.handleErrorWith(fa.value)(f.andThen(_.value)))

        def raiseError[A](e: E): IorT[F.Case, L, A] =
          IorT.liftF(F.CaseInstance.raiseError[A](e))(F.CaseInstance)

        def ap[A, B](ff: IorT[F.Case, L, A => B])(fa: IorT[F.Case, L, A]): IorT[F.Case, L, B] =
          IorT {
            F.CaseInstance.map(F.CaseInstance.product(ff.value, fa.value)) {
              case (iorab, iora) => (iorab, iora).mapN(_(_))
            }
          }
      }

      def pure[A](x: A): IorT[F, L, A] =
        delegate.pure(x)

      def handleErrorWith[A](fa: IorT[F, L, A])(f: E => IorT[F, L, A]): IorT[F, L, A] =
        delegate.handleErrorWith(fa)(f)

      def raiseError[A](e: E): IorT[F, L, A] =
        delegate.raiseError(e)

      def bracketCase[A, B](
        acquire: IorT[F, L, A]
      )(use: A => IorT[F, L, B])(release: (A, Case[B]) => IorT[F, L, Unit]): IorT[F, L, B] =
        IorT {
          F.bracketCase(acquire.value)((ior: Ior[L, A]) => ior.flatTraverse(a => use(a).value)) {
            (ior: Ior[L, A], resultIor: F.Case[Ior[L, B]]) =>
              val resultsF = ior.flatTraverse { a =>
                release(a, IorT(resultIor)).value
              }

              resultsF.void
          }
        }

      def flatMap[A, B](fa: IorT[F, L, A])(f: A => IorT[F, L, B]): IorT[F, L, B] =
        delegate.flatMap(fa)(f)

      def tailRecM[A, B](a: A)(f: A => IorT[F, L, Either[A, B]]): IorT[F, L, B] =
        delegate.tailRecM(a)(f)
    }

  implicit def bracketForKleisli[F[_], R, E](
    implicit F: Bracket[F, E]
  ): Bracket.Aux[Kleisli[F, R, *], E, Kleisli[F.Case, R, *]] =
    new Bracket[Kleisli[F, R, *], E] {

      private[this] val delegate = Kleisli.catsDataMonadErrorForKleisli[F, R, E]

      type Case[A] = Kleisli[F.Case, R, A]

      // TODO put this into cats-core
      def CaseInstance: ApplicativeError[Case, E] = new ApplicativeError[Case, E] {

        def pure[A](x: A): Kleisli[F.Case, R, A] = Kleisli.pure[F.Case, R, A](x)(F.CaseInstance)

        def handleErrorWith[A](fa: Kleisli[F.Case, R, A])(f: E => Kleisli[F.Case, R, A]): Kleisli[F.Case, R, A] =
          Kleisli(r => F.CaseInstance.handleErrorWith(fa.run(r))(f.andThen(_.run(r))))

        def raiseError[A](e: E): Kleisli[F.Case, R, A] =
          Kleisli.liftF(F.CaseInstance.raiseError[A](e))

        def ap[A, B](ff: Kleisli[F.Case, R, A => B])(fa: Kleisli[F.Case, R, A]): Kleisli[F.Case, R, B] =
          Kleisli { r =>
            F.CaseInstance.map(F.CaseInstance.product(ff.run(r), fa.run(r))) {
              case (fab, a) => fab(a)
            }
          }
      }

      def pure[A](x: A): Kleisli[F, R, A] =
        delegate.pure(x)

      def handleErrorWith[A](fa: Kleisli[F, R, A])(f: E => Kleisli[F, R, A]): Kleisli[F, R, A] =
        delegate.handleErrorWith(fa)(f)

      def raiseError[A](e: E): Kleisli[F, R, A] =
        delegate.raiseError(e)

      def bracketCase[A, B](
        acquire: Kleisli[F, R, A]
      )(use: A => Kleisli[F, R, B])(release: (A, Case[B]) => Kleisli[F, R, Unit]): Kleisli[F, R, B] =
        Kleisli { r =>
          F.bracketCase(acquire.run(r))((a: A) => use(a).run(r)) { (a: A, result: F.Case[B]) =>
            val resultsF = release(a, Kleisli.liftF(result)).run(r)

            resultsF.void
          }
        }

      def flatMap[A, B](fa: Kleisli[F, R, A])(f: A => Kleisli[F, R, B]): Kleisli[F, R, B] =
        delegate.flatMap(fa)(f)

      def tailRecM[A, B](a: A)(f: A => Kleisli[F, R, Either[A, B]]): Kleisli[F, R, B] =
        delegate.tailRecM(a)(f)
    }
}

trait Region[R[_[_], _], F[_], E] extends Safe[R[F, *], E] {

  def openCase[A, e](acquire: F[A])(release: (A, Case[e]) => F[Unit]): R[F, A]

  def open[A](acquire: F[A])(release: A => F[Unit]): R[F, A] =
    openCase(acquire)((a: A, _: Case[Unit]) => release(a))

  def liftF[A](fa: F[A]): R[F, A]

  // this is analogous to *>, but with more constrained laws (closing the resource scope)
  def supersededBy[B, e](rfa: R[F, e], rfb: R[F, B]): R[F, B]

  // this is analogous to void, but it closes the resource scope
  def close[e](rfa: R[F, e]): R[F, Unit] = supersededBy(rfa, unit)
}

object Region {
  type Aux[R[_[_], _], F[_], E, Case0[_]] = Region[R, F, E] { type Case[A] = Case0[A] }
  type Aux2[R[_[_], _], F[_], E, Case0[_, _]] = Region[R, F, E] { type Case[A] = Case0[E, A] }

  def apply[R[_[_], _], F[_], E](implicit R: Region[R, F, E]): R.type = R
  def apply[R[_[_], _], F[_]](implicit R: Region[R, F, _], d1: DummyImplicit): R.type = R
}
