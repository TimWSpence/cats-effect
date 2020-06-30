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
import cats.{Parallel, Traverse}

import scala.concurrent.{ExecutionContext, Future}

trait Async[F[_]] extends Sync[F] with Temporal[F, Throwable] { self: Safe[F, Throwable] =>

  // returns an optional cancelation token
  def async[A](k: (Either[Throwable, A] => Unit) => F[Option[F[Unit]]]): F[A]

  def asyncF[A](k: (Either[Throwable, A] => Unit) => F[Unit]): F[A] = ???

  def never[A]: F[A] = async((_: Either[Throwable, A] => Unit) => pure(none[F[Unit]]))

  // evalOn(executionContext, ec) <-> pure(ec)
  def evalOn[A](fa: F[A], ec: ExecutionContext): F[A]
  def executionContext: F[ExecutionContext]
}

object Async {
  def apply[F[_]](implicit F: Async[F]): F.type = F

  def fromFuture[F[_], A](fa: F[Future[A]])(implicit F: Async[F]): F[A] = ???

  def liftIO[F[_], A](io: IO[A])(implicit F: Async[F]): F[A] = ???

  def memoize[F[_], A](f: F[A])(implicit F: Async[F]): F[F[A]] = ???

  def parTraverseN[T[_]: Traverse, M[_], A, B](n: Long)(ta: T[A])(f: A => M[B])(implicit M: Async[M],
                                                                                P: Parallel[M]): M[T[B]] = ???

  def parSequenceN[T[_]: Traverse, M[_], A](n: Long)(tma: T[M[A]])(implicit M: Async[M], P: Parallel[M]): M[T[A]] = ???
}
