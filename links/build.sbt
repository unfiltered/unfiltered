description := "Support for coherent Link header construction"

unmanagedClasspath in (local("links"), Test) <++=
  (fullClasspath in (local("specs2"), Compile),
    fullClasspath in (local("scalatest"), Compile)) map { _ ++ _ }