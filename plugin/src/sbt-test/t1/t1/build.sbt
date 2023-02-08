Compile / PB.targets ++= Seq[protocbridge.Target](
  scalapb.gen(grpc = true, flatPackage = true) -> (Compile / sourceManaged).value / "scalapb"
)

libraryDependencies ++= Seq(
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
  "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
)

TaskKey[Unit]("checkGitInfoNonEmpty") := {
  val x = (LocalRootProject / unusedProto).value.values
  List(
    x.methods,
    x.enums,
    x.messages,
  ).flatten.foreach { a =>
    assert(a.gitInfo.exists(_.commit.nonEmpty), a)
  }
}
