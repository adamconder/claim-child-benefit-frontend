/*
 * Copyright 2023 HM Revenue & Customs
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

package utils

import cats.implicits._
import cats.Monad

import scala.language.higherKinds

object MonadOps {

  implicit class BooleanMonadSyntax[F[_]](fa: F[Boolean])(implicit m: Monad[F]) {

    def &&(that: F[Boolean]): F[Boolean] = fa.flatMap {
      case false => m.pure(false)
      case true  => that
    }

    def ||(that: F[Boolean]): F[Boolean] = fa.flatMap {
      case true  => m.pure(true)
      case false => that
    }
  }
}
