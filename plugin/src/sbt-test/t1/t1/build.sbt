Compile / PB.targets ++= Seq[protocbridge.Target](
  scalapb.gen(grpc = true, flatPackage = true) -> (Compile / sourceManaged).value / "scalapb"
)

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
  val expect = Set("RpcMethodName1", "RpcMethodName3", "Enum1", "A")
  assert(names == expect, names)
}
