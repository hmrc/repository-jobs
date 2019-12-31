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
import org.scalacheck.{Arbitrary, Gen}
import uk.gov.hmrc.repositoryjobs.JenkinsBuildJobsResponse.flattenJobs
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class JenkinsBuildJobsResponseSpec extends AnyWordSpec with Matchers with ScalaCheckDrivenPropertyChecks {
  "flattenJobs" should {
    "flatten a nested list of folders and jobs" in {
      val job1    = Job(Some("job"), None, Nil, None)
      val job2    = Job(Some("another job"), None, Nil, None)
      val jobTree = Folder(Some("a Folder"), Seq(Folder(Some("a nested Folder"), Seq(job1))))
      val jobs    = Seq(job2, jobTree)

      flattenJobs(JenkinsBuildJobsResponse(jobs)).jobs should contain theSameElementsAs Seq(job2, job1)
    }

    "flatten an arbitrary list of nested folders" in {
      forAll(genListJobTree) { jobTrees =>
        flattenJobs(JenkinsBuildJobsResponse(jobTrees)).jobs should contain theSameElementsAs jobTrees.flatMap(
          jobTree => flattenJobs(JenkinsBuildJobsResponse(Seq(jobTree))).jobs)
      }
    }
  }

  val genJobTree: Gen[JobTree] =
    Arbitrary {
      val genJob: Gen[Job] = for {
        name      <- Gen.option(Gen.alphaStr)
        url       <- Gen.const(None)
        allBuilds <- Gen.const(Nil)
        scm       <- Gen.const(None)
      } yield Job(name, url, allBuilds, scm)

      def genFolder(sz: Int): Gen[JobTree] =
        for {
          name <- Gen.option(Gen.alphaStr)
          n    <- Gen.choose(sz / 3, sz / 2)
          c    <- Gen.listOfN(n, sizedJobTree(sz / 2))
        } yield Folder(name, c)

      def sizedJobTree(sz: Int): Gen[JobTree] =
        if (sz <= 0) genJob
        else Gen.frequency((1, genJob), (3, genFolder(sz)))

      Gen.sized(sz => sizedJobTree(sz))
    }.arbitrary

  val genListJobTree: Gen[List[JobTree]] = Gen.listOfN(20, Gen.resize(4, genJobTree))
}
