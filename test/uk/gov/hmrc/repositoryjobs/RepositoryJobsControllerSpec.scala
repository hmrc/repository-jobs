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

import org.mockito.Matchers._
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.play.test.UnitSpec
import cats.syntax.option._

class RepositoryJobsControllerSpec extends UnitSpec with MockitoSugar with OneAppPerSuite {

  "get repository builds" should {

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
      when(mockBuildRepository.getForRepository(any())).thenReturn(Seq(build1, build2))

      val controller = controllerWithData(mockBuildRepository)

      val builds = contentAsJson(controller.builds("repository-abcd").apply(FakeRequest())).as[Seq[Build]]

      Mockito.verify(mockBuildRepository).getForRepository("repository-abcd")

      builds.size shouldBe 2
      builds.head shouldBe build1
      builds.last shouldBe build2
    }

    "return 404 response if repository build not found" in {
      val mockBuildRepository: BuildsRepository = mock[BuildsRepository]
      when(mockBuildRepository.getForRepository(any())).thenReturn(Nil)

      val controller = controllerWithData(mockBuildRepository)

      val response = controller.builds("non-existing-repository").apply(FakeRequest())

      status(response) shouldBe NOT_FOUND

      contentAsString(response) shouldBe "No build found for 'non-existing-repository'"
    }

  }

  def controllerWithData(buildsRepository: BuildsRepository): RepositoryJobsController =
    new RepositoryJobsController(buildsRepository)

}
