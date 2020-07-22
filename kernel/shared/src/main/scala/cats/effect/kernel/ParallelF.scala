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

import cats.{Align, Applicative, Functor}
import cats.data.Ior
import cats.implicits._
import cats.effect.implicits._

object Par {

  type ParallelF[F[_], A] = F[A] with ParallelF.type

  object ParallelF {

    def apply[F[_], A](fa: F[A]): ParallelF[F, A] = fa.asInstanceOf[ParallelF[F, A]]

    def value[F[_], A](t: ParallelF[F, A]): F[A] = t

    implicit def applicativeForParallelF[F[_], E](
        implicit F: Concurrent[F, E]): Applicative[ParallelF[F, *]] =
      new Applicative[ParallelF[F, *]] {

        def pure[A](a: A): ParallelF[F, A] = ParallelF(F.pure(a))

        override def map[A, B](fa: ParallelF[F, A])(f: A => B): ParallelF[F, B] =
          ParallelF(value(fa).map(f))

        def ap[A, B](ff: ParallelF[F, A => B])(fa: ParallelF[F, A]): ParallelF[F, B] =
          ParallelF(
            F.both(value(ff), value(fa)).map {
              case (f, a) => f(a)
            }
          )

      }

    implicit def alignForParallelF[F[_], E](
        implicit F: Concurrent[F, E]): Align[ParallelF[F, *]] =
      new Align[ParallelF[F, *]] {

        override def functor: Functor[ParallelF[F, *]] = applicativeForParallelF[F, E]

        override def align[A, B](
            fa: ParallelF[F, A],
            fb: ParallelF[F, B]): ParallelF[F, Ior[A, B]] =
          alignWith(fa, fb)(identity)

        override def alignWith[A, B, C](fa: ParallelF[F, A], fb: ParallelF[F, B])(
            f: Ior[A, B] => C): ParallelF[F, C] =
          ParallelF(
            (value(fa).attempt, value(fb).attempt)
              .parMapN((ea, eb) => catsStdInstancesForEither.alignWith(ea, eb)(f))
              .flatMap(F.fromEither)
          )

      }

  }

}
