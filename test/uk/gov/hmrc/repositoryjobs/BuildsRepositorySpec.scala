/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.repositoryjobs

import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.IndexType.Descending
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}
import uk.gov.hmrc.play.test.UnitSpec
import scala.concurrent.ExecutionContext.Implicits.global

class BuildsRepositorySpec extends UnitSpec with Matchers with MongoSpecSupport with ScalaFutures {

  "Builds repository" should {
    "ensure indexes are created" in new Setup {
      val indexes = repository.collection.indexesManager.list().futureValue

      indexes.map(index => index.key -> index.background) should contain allOf (
        Seq("repositoryName" -> Descending) -> true,
        Seq("jobName"        -> Descending, "timestamp" -> Descending) -> true
      )
    }
  }

  private trait Setup {
    await(mongo().drop())

    val repository = new BuildsRepository(new ReactiveMongoComponent {
      override def mongoConnector: MongoConnector = mongoConnectorForTest
    })
  }
}
