description := "Facilitates testing Unfiltered servers with Specs2"

libraryDependencies <++= scalaVersion { v =>
  Common.specs2Dep(v) :: Common.dispatchDeps
}
