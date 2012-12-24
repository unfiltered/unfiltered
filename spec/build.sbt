description := "Facilitates testing Unfiltered servers with Specs"

libraryDependencies <++= scalaVersion { v =>
  Common.specsDep(v) :: Common.dispatchDeps
}
