description := "Facilitates testing Unfiltered servers with Specs"

libraryDependencies <++= scalaVersion { v =>
  Shared.specsDep(v) :: Shared.dispatchDeps
}
