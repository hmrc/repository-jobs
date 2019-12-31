/*
 * Copyright 2019 HM Revenue & Customs
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

import cats.syntax.option._
import org.mockito.{ArgumentMatchersSugar, Mockito, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RepositoryJobsControllerSpec extends AnyWordSpec with Matchers with MockitoSugar with ArgumentMatchersSugar with GuiceOneAppPerSuite {//extends UnitSpec with MockitoSugar  {

  "get repository builds" should {

    def controllerWithBuildsRepository(buildsRepository: BuildsRepository) =
      new RepositoryJobsController(buildsRepository, mock[RepositoryJobsService], stubControllerComponents())

    "return a json formatted response containing info about the builds for that repository" in {

      val build1 = Build(
        "repo-name".some,
        "repository-abcd".some,
        "job.url".some,
        1.some,
        "result-xyz".some,
        1l.some,
        20.some,
        "build.url".some,
        "built-on".some)
      val build2 = Build(
        "repo-name".some,
        "repository-abcd".some,
        "job.url".some,
        5.some,
        "result-xyz".some,
        2l.some,
        30.some,
        "build.url".some,
        "built-on".some)

      val mockBuildRepository: BuildsRepository = mock[BuildsRepository]
      when(mockBuildRepository.getForRepository(any)).thenReturn(Future(Seq(build1, build2)))

      val controller = controllerWithBuildsRepository(mockBuildRepository)

      val builds = contentAsJson(controller.builds("repository-abcd").apply(FakeRequest())).as[Seq[Build]]

      Mockito.verify(mockBuildRepository).getForRepository("repository-abcd")

      builds.size shouldBe 2
      builds.head shouldBe build1
      builds.last shouldBe build2
    }

    "return 404 response if repository build not found" in {
      val mockBuildRepository: BuildsRepository = mock[BuildsRepository]
      when(mockBuildRepository.getForRepository(any)).thenReturn(Future(Nil))

      val controller = controllerWithBuildsRepository(mockBuildRepository)

      val response = controller.builds("non-existing-repository").apply(FakeRequest())

      status(response) shouldBe NOT_FOUND

      contentAsString(response) shouldBe "No build found for 'non-existing-repository'"
    }

  }

  "reload repository jobs cache" should {
    def controllerWithRepositoryJobService(repositoryJobsService: RepositoryJobsService) =
      new RepositoryJobsController(mock[BuildsRepository], repositoryJobsService, stubControllerComponents())

    "return 200 OK if all updates are successful" in {
      val mockRepositoryJobService = mock[RepositoryJobsService]
      val expectedResult           = UpdateResult(nSuccesses = 1, nFailures = 0)

      when(mockRepositoryJobService.update).thenReturn(Future(expectedResult))
      val controller = controllerWithRepositoryJobService(mockRepositoryJobService)

      val response = controller.reload().apply(FakeRequest())

      status(response) shouldBe OK

      contentAsJson(response) shouldBe Json.toJson(expectedResult)
    }

    "return 500 Internal Server Error" in {
      val mockRepositoryJobService = mock[RepositoryJobsService]
      val expectedResult           = UpdateResult(nSuccesses = 1, nFailures = 1)

      when(mockRepositoryJobService.update).thenReturn(Future(expectedResult))
      val controller = controllerWithRepositoryJobService(mockRepositoryJobService)

      val response = controller.reload().apply(FakeRequest())

      status(response) shouldBe INTERNAL_SERVER_ERROR

      contentAsJson(response) shouldBe Json.toJson(expectedResult)
    }

  }
}
