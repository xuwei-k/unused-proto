Compile / PB.targets ++= Seq[protocbridge.Target](
  scalapb.gen(grpc = true, flatPackage = true) -> (Compile / sourceManaged).value / "scalapb"
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

TaskKey[Unit]("updateProto") := {
  val dir = (Compile / sourceDirectory).value / "protobuf"
  val f = dir / "A.proto"
  val newLines = IO.readLines(f).map { line =>
    line.replace("string xxx = 1", "  string xxx = 1")
  }
  IO.writeLines(f, newLines)
  println(dir)
  assert(sys.process.Process(s"git add -- ${dir}").! == 0)
  assert(sys.process.Process(s"git commit -m update").! == 0)
}

TaskKey[Unit]("updateIgnoreRevs") := {
  val hash = sys.process.Process("git rev-parse HEAD").lineStream_!.head
  val f = file(".git-blame-ignore-revs")
  IO.writeLines(f, hash :: Nil)
  assert(sys.process.Process(s"git add -- ${f.getName}").! == 0)
  assert(sys.process.Process(s"git commit -m add").! == 0)
}

InputKey[Unit]("checkGitInfoLastUpdated") := {
  import complete.DefaultParsers.*
  val expectNames: Set[String] = spaceDelimited("<arg>").parsed.toSet

  val x = (LocalRootProject / unusedProto).value.values
  type AsComparable[A] = A => Comparable[? >: A]

  implicit def orderingFromJavaComparable[A](implicit a: AsComparable[A]): Ordering[A] =
    (x: A, y: A) => a(x).compareTo(y)

  println(
    List(
      x.methods,
      x.enums,
      x.messages,
    ).flatten.flatMap { a =>
      a.gitInfo.map(_.date -> a.name)
    }
  )

  val values = List(
    x.methods,
    x.enums,
    x.messages,
  ).flatten.flatMap { a =>
    a.gitInfo.map(_.date -> a.name)
  }.groupBy(_._1).toList.maxBy(_._1)._2.toSet
  assert(values.map(_._2) == expectNames, values)
}
