# repository-jobs

[ ![Download](https://api.bintray.com/packages/hmrc/releases/repository-jobs/images/download.svg) ](https://bintray.com/hmrc/releases/repository-jobs/_latestVersion)

This service provides information about the build jobs for repositories.

#### How it works
The service collects information from Jenkins on a scheduler, and stores in a Mongo repository. The store can also be updated on request through the API.
The build jobs is served from the Mongo repository.

#### Configuration
````
scheduler.enabled # enable the scheduler
````

### License


This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
