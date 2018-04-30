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

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{Matchers, WordSpec}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.IndexType.Descending
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}

import scala.concurrent.ExecutionContext.Implicits.global

class BuildsRepositorySpec extends WordSpec with Matchers with MongoSpecSupport with ScalaFutures with IntegrationPatience {

  "Builds repository" should {
    "ensure indexes are created" in new Setup {
      val indexes = repository.collection.indexesManager.list().futureValue

      indexes.map(index => index.key -> index.background) should contain allOf(
        Seq("repositoryName" -> Descending) -> true,
        Seq("jobName" -> Descending, "timestamp" -> Descending) -> true
      )
    }
  }

  "save" should {

    "updates existing jobs and insert new ones" in new Setup {
      val existingBuild = Build(
        repositoryName = Some("repo1"),
        jobName = Some("job1"),
        jobUrl = Some("jobUrl1"),
        buildNumber = Some(1),
        result = Some("result1"),
        timestamp = Some(System.currentTimeMillis()),
        duration = Some(1),
        buildUrl = Some("buildUrl1"),
        builtOn = Some("agent1")
      )

      repository.insert(existingBuild).futureValue
      repository.count.futureValue shouldBe 1

      val newBuild = Build(
        repositoryName = Some("repo2"),
        jobName = Some("job2"),
        jobUrl = Some("jobUrl2"),
        buildNumber = Some(2),
        result = Some("result2"),
        timestamp = Some(System.currentTimeMillis()),
        duration = Some(2),
        buildUrl = Some("buildUrl2"),
        builtOn = Some("agent2")
      )

      val updatedBuild = existingBuild.copy(result = Some("result1Updated"))

      repository.persist(Seq(updatedBuild, newBuild)).futureValue shouldBe UpdateResult(nSuccesses = 2, nFailures = 0)

      repository.getAll.futureValue should contain theSameElementsAs Seq(updatedBuild, newBuild)
    }
  }

  private trait Setup {
    mongo().drop().futureValue

    val repository = new BuildsRepository(new ReactiveMongoComponent {
      override def mongoConnector: MongoConnector = mongoConnectorForTest
    })
  }
}
