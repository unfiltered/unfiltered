description := "Facilitates testing Unfiltered servers with Specs2"

libraryDependencies ++= {
  Common.specs2Dep(scalaVersion.value) :: Common.okHttp
}
