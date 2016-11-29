// https://github.com/sbt/sbt/issues/85#issuecomment-1687483

unmanagedClasspath in Compile += Attributed.blank(
  new java.io.File("doesnotexist")
)
