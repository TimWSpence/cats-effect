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

import cats.{~>, ApplicativeError, MonadError}
import cats.syntax.either._
import cats.data._

trait Fiber[F[_], E, A] {
  def cancel: F[Unit]
  def join: F[Outcome[F, E, A]]
}

trait Concurrent[F[_], E] extends MonadError[F, E] { self: Safe[F, E] =>
  type Case[A] = Outcome[F, E, A]

  final def CaseInstance: ApplicativeError[Outcome[F, E, *], E] =
    Outcome.applicativeError[F, E](this)

  def start[A](fa: F[A]): F[Fiber[F, E, A]]

  def uncancelable[A](body: (F ~> F) => F[A]): F[A]

  // produces an effect which is already canceled (and doesn't introduce an async boundary)
  // this is effectively a way for a fiber to commit suicide and yield back to its parent
  // The fallback (unit) value is produced if the effect is sequenced into a block in which
  // cancelation is suppressed.
  def canceled: F[Unit]

  // produces an effect which never returns
  def never[A]: F[A]

  // introduces a fairness boundary by yielding control to the underlying dispatcher
  def cede: F[Unit]

  def racePair[A, B](
      fa: F[A],
      fb: F[B]
  ): F[Either[(A, Fiber[F, E, B]), (Fiber[F, E, A], B)]]

  def race[A, B](fa: F[A], fb: F[B]): F[Either[A, B]] =
    flatMap(racePair(fa, fb)) {
      case Left((a, f))  => as(f.cancel, a.asLeft[B])
      case Right((f, b)) => as(f.cancel, b.asRight[A])
    }

  def both[A, B](fa: F[A], fb: F[B]): F[(A, B)] =
    flatMap(racePair(fa, fb)) {
      case Left((a, f)) =>
        flatMap(f.join) { c =>
          c.fold(
            flatMap(canceled)(_ => never), // if our child canceled, then we must also be cancelable since racePair forwards our masks along, so it's safe to use never
            e => raiseError[(A, B)](e),
            tupleLeft(_, a)
          )
        }

      case Right((f, b)) =>
        flatMap(f.join) { c =>
          c.fold(
            flatMap(canceled)(_ => never),
            e => raiseError[(A, B)](e),
            tupleRight(_, b)
          )
        }
    }
}

object Concurrent {
  def apply[F[_], E](implicit F: Concurrent[F, E]): F.type = F
  def apply[F[_]](implicit F: Concurrent[F, _], d: DummyImplicit): F.type = F

  trait OptionTConcurrentBracket[F[_], E]
      extends Concurrent[OptionT[F, *], E] with Bracket.OptionTBracket[F, E] {

    implicit protected def F: ConcurrentBracket[F, E]

    override def start[A](
        fa: OptionT[F, A]
    ): OptionT[F, Fiber[OptionT[F, *], E, A]] = ???


    override def uncancelable[A](
        body: OptionT[F, *] ~> OptionT[F, *] => OptionT[F, A]
    ): OptionT[F, A] = ???

    override def canceled: OptionT[F, Unit] = OptionT.liftF(F.canceled)

    override def never[A]: OptionT[F, A] = OptionT.liftF(F.never)

    override def cede: OptionT[F, Unit] = OptionT.liftF(F.cede)

    override def racePair[A, B](fa: OptionT[F, A], fb: OptionT[F, B]): OptionT[
      F,
      Either[(A, Fiber[OptionT[F, *], E, B]), (Fiber[OptionT[F, *], E, A], B)]
    ] = ???

  }

  // trait EitherTConcurrent[F[_], E1, E2]
  //     extends Concurrent[EitherT[F, E1, *], E2]
  //     with Bracket.EitherTBracket[F, E1, E2] {

  //   override def start[A](
  //       fa: EitherT[F, E1, A]
  //   ): EitherT[F, E1, Fiber[EitherT[F, E1, *], E2, A]] = ???

  //   override def uncancelable[A](
  //       body: EitherT[F, E1, *] ~> EitherT[F, E1, *] => EitherT[F, E1, A]
  //   ): EitherT[F, E1, A] = ???

  //   override def canceled: EitherT[F, E1, Unit] = ???

  //   override def never[A]: EitherT[F, E1, A] = ???

  //   override def cede: EitherT[F, E1, Unit] = ???

  //   override def racePair[A, B](
  //       fa: EitherT[F, E1, A],
  //       fb: EitherT[F, E1, B]
  //   ): EitherT[F, E1, Either[
  //     (A, Fiber[EitherT[F, E1, *], E2, B]),
  //     (Fiber[EitherT[F, E1, *], E2, A], B)
  //   ]] = ???

  // }

  // trait StateTConcurrent[F[_], S, E]
  //     extends Concurrent[StateT[F, S, *], E]
  //     with Bracket.StateTBracket[F, S, E] {

  //   override def start[A](
  //       fa: IndexedStateT[F, S, S, A]
  //   ): IndexedStateT[F, S, S, Fiber[IndexedStateT[F, S, S, *], E, A]] = ???

  //   override def uncancelable[A](
  //       body: IndexedStateT[F, S, S, *] ~> IndexedStateT[F, S, S, *] => IndexedStateT[
  //         F,
  //         S,
  //         S,
  //         A
  //       ]
  //   ): IndexedStateT[F, S, S, A] = ???

  //   override def canceled: IndexedStateT[F, S, S, Unit] = ???

  //   override def never[A]: StateT[F, S, A] = ???

  //   override def cede: IndexedStateT[F, S, S, Unit] = ???

  //   override def racePair[A, B](
  //       fa: IndexedStateT[F, S, S, A],
  //       fb: IndexedStateT[F, S, S, B]
  //   ): IndexedStateT[F, S, S, Either[
  //     (A, Fiber[IndexedStateT[F, S, S, *], E, B]),
  //     (Fiber[IndexedStateT[F, S, S, *], E, A], B)
  //   ]] = ???

  // }

  // trait WriterTConcurrent[F[_], S, E]
  //     extends Concurrent[WriterT[F, S, *], E]
  //     with Bracket.WriterTBracket[F, S, E] {

  //     override def start[A](fa: WriterT[F,S,A]): WriterT[F,S,Fiber[WriterT[F,S,*],E,A]] = ???

  //     override def uncancelable[A](body: WriterT[F,S,*] ~> WriterT[F,S,*] => WriterT[F,S,A]): WriterT[F,S,A] = ???

  //     override def canceled: WriterT[F,S,Unit] = ???

  //     override def never[A]: WriterT[F, S, A] = ???

  //     override def cede: WriterT[F,S,Unit] = ???

  //     override def racePair[A, B](fa: WriterT[F,S,A], fb: WriterT[F,S,B]): WriterT[F,S,Either[(A, Fiber[WriterT[F,S,*],E,B]),(Fiber[WriterT[F,S,*],E,A], B)]] = ???

  // }

  // trait IorTConcurrent[F[_], L, E]
  //     extends Concurrent[IorT[F, L, *], E]
  //     with Bracket.IorTBracket[F, L, E] {

  //     override def start[A](fa: IorT[F,L,A]): IorT[F,L,Fiber[IorT[F,L,*],E,A]] = ???

  //     override def uncancelable[A](body: IorT[F,L,*] ~> IorT[F,L,*] => IorT[F,L,A]): IorT[F,L,A] = ???

  //     override def canceled: IorT[F,L,Unit] = ???

  //     override def never[A]: IorT[F, L, A] = ???

  //     override def cede: IorT[F,L,Unit] = ???

  //     override def racePair[A, B](fa: IorT[F,L,A], fb: IorT[F,L,B]): IorT[F,L,Either[(A, Fiber[IorT[F,L,*],E,B]),(Fiber[IorT[F,L,*],E,A], B)]] = ???

  // }

  // trait KleisliConcurrent[F[_], R, E]
  //     extends Concurrent[Kleisli[F, R, *], E]
  //     with Bracket.KleisliBracket[F, R, E] {

  //     override def start[A](fa: Kleisli[F,R,A]): Kleisli[F,R,Fiber[Kleisli[F,R,*],E,A]] = ???

  //     override def uncancelable[A](body: Kleisli[F,R,*] ~> Kleisli[F,R,*] => Kleisli[F,R,A]): Kleisli[F,R,A] = ???

  //     override def canceled: Kleisli[F,R,Unit] = ???

  //     override def never[A]: Kleisli[F, R, A] = ???

  //     override def cede: Kleisli[F,R,Unit] = ???

  //     override def racePair[A, B](fa: Kleisli[F,R,A], fb: Kleisli[F,R,B]): Kleisli[F,R,Either[(A, Fiber[Kleisli[F,R,*],E,B]),(Fiber[Kleisli[F,R,*],E,A], B)]] = ???

  // }
}
