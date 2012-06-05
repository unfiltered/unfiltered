// https://github.com/harrah/xsbt/issues/85#issuecomment-1687483

unmanagedClasspath in Compile += Attributed.blank(
  new java.io.File("doesnotexist")
)
