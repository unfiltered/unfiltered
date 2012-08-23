description := "Facilitates testing Unfiltered servers with Specs2"

libraryDependencies <++= scalaVersion { v =>
  Shared.specs2Dep(v) :: Shared.dispatchDeps
}

resolvers += "scala-tools snapshots" at "http://scala-tools.org/repo-snapshots/"