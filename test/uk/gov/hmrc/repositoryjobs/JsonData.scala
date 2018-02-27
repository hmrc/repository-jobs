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

object JsonData {

  val jenkinsJobsResponse =
    s"""{
        |    "jobs":[
        |        {
        |            "name":"address-lookup",
        |            "url":"https://ci/job/address-lookup/",
        |            "allBuilds":[
        |                {
        |                    "description":"1ccc1869ee8fcd8ff28701b5804c880106b38771",
        |                    "duration":218869,
        |                    "id":"2017-02-08_16-32-42",
        |                    "number":126,
        |                    "result":"SUCCESS",
        |                    "timestamp":1486571562000,
        |                    "url":"https://ci/job/address-lookup/126/",
        |                    "builtOn":"ci-slave-9"
        |                }
        |            ],
        |            "scm":{
        |                "userRemoteConfigs":[
        |                    {
        |                        "url":"git@github:HMRC/address-lookup.git"
        |                    }
        |                ]
        |            }
        |        }
        |    ]
        |}
     """.stripMargin

  private val controlCharacters = ((1.toChar to 31.toChar).toList :+ 127.toChar).mkString

  val jenkinsJobsResponseWithControlCharacters =
    s"""{
        |    "jobs":[
        |        {
        |            "name":"address-lookup$controlCharacters",
        |            "url":"https://ci/job/address-lookup/",
        |            "allBuilds":[
        |            ],
        |            "scm":{
        |            }
        |        }
        |    ]
        |}
     """.stripMargin
}
