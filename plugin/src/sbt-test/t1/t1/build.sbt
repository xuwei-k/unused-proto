val workaroundScalaPB2112 = Def.setting {
  // https://github.com/scalapb/ScalaPB/pull/2112
  val v = sbtBinaryVersion.value match {
    case "1.0" =>
      "2.12"
    case "2" =>
      "3"
  }
  protocbridge.SandboxedJvmGenerator.forModule(
    "scala",
    protocbridge.Artifact(
      "com.thesamet.scalapb",
      s"compilerplugin_${v}",
      scalapb.compiler.Version.scalapbVersion
    ),
    "scalapb.ScalaPbCodeGenerator$",
    scalapb.ScalaPbCodeGenerator.suggestedDependencies
  )
}

Compile / PB.targets ++= Seq[protocbridge.Target](
  scalapb.gen(grpc = true, flatPackage = true).copy(_1 = workaroundScalaPB2112.value) -> (Compile / sourceManaged).value / "scalapb"
)

unusedProto / logLevel := Level.Info

libraryDependencies ++= Seq(
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
  "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
)

TaskKey[Unit]("checkGitInfo") := {
  val x = (LocalRootProject / unusedProto).value.values
  val names = List(
    x.methods,
    x.enums,
    x.messages,
  ).flatten.filter(_.gitInfo.exists(_.commit.nonEmpty)).map(_.name).toSet
  val expect = Set("RpcMethodName1", "RpcMethodName3", "Enum1", "A", "Symlink")
  assert(names == expect, names)
}
