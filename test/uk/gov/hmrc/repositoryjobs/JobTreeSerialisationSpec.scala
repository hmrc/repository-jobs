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
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json._

class JobTreeSerialisationSpec extends WordSpec with Matchers {
  "Reads of JobTree" should {
    "deserialise a JobTree" in {
      Json.parse(folderJson).validate[Option[JobTree]] shouldBe JsSuccess(Some(folder))
    }
  }

  val folder =
    Folder(
      Some("PlatOps Examples"),
      Seq(
        Job(
          Some("platops-example-api-tests"),
          None,
          Nil,
          Some(Scm(Some(Seq(UserRemoteConfig(Some("https://github.com/hmrc/platops-example-api-tests.git"))))))))
    )

  val folderJson: String =
    s"""
       |    {
       |      "_class": "com.cloudbees.hudson.plugins.folder.Folder",
       |      "name": "PlatOps Examples",
       |      "jobs": [
       |        {
       |          "_class": "hudson.model.FreeStyleProject",
       |          "name": "platops-example-api-tests",
       |          "allBuilds": [],
       |          "scm": {
       |            "_class": "hudson.plugins.git.GitSCM",
       |            "userRemoteConfigs": [
       |              {
       |                "url": "https://github.com/hmrc/platops-example-api-tests.git"
       |              }
       |            ]
       |          }
       |        },
       |        {
       |          "_class": "org.jenkinsci.plugins.workflow.job.WorkflowJob",
       |          "name": "platops-example-backend-microservice-pipeline",
       |          "allBuilds":[]
       |        }
       |      ]
       |    }
     """.stripMargin

}
