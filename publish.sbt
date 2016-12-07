credentials ++= {
  val cred = Path.userHome / ".sbt" / "sonatype.credentials"
  if (cred.exists) Seq(Credentials(cred)) else Nil
}
