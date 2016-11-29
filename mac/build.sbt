description := "MAC utilities for oauth2 module"

unmanagedClasspath in (local("mac"), Test) ++=
  (fullClasspath in (local("specs2"), Compile)).value
