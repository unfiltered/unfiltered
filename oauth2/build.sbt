description := "OAuth2 module for unfiltered"

unmanagedClasspath in (local("oauth2"), Test) ++=
  (fullClasspath in (local("specs2"), Compile)).value
