import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations.*

def Scala212 = "2.12.18"
def Scala3 = "3.3.0"

val commonSettings = Def.settings(
  publishTo := sonatypePublishToBundle.value,
  Compile / unmanagedResources += (LocalRootProject / baseDirectory).value / "LICENSE.txt",
  Compile / packageSrc / mappings ++= (Compile / managedSources).value.map { f =>
    (f, f.relativeTo((Compile / sourceManaged).value).get.getPath)
  },
  Compile / doc / scalacOptions ++= {
    val hash = sys.process.Process("git rev-parse HEAD").lineStream_!.head
    if (scalaBinaryVersion.value != "3") {
      Seq(
        "-sourcepath",
        (LocalRootProject / baseDirectory).value.getAbsolutePath,
        "-doc-source-url",
        s"https://github.com/xuwei-k/unused-proto/blob/${hash}€{FILE_PATH}.scala"
      )
    } else {
      Nil
    }
  },
  scalacOptions ++= Seq(
    "-deprecation",
  ),
  scalacOptions ++= {
    if (scalaBinaryVersion.value == "3") {
      Nil
    } else {
      Seq(
        "-Xsource:3",
      )
    }
  },
  pomExtra := (
    <developers>
    <developer>
      <id>xuwei-k</id>
      <name>Kenji Yoshida</name>
      <url>https://github.com/xuwei-k</url>
    </developer>
  </developers>
  <scm>
    <url>git@github.com:xuwei-k/unused-proto.git</url>
    <connection>scm:git:git@github.com:xuwei-k/unused-proto.git</connection>
  </scm>
  ),
  organization := "com.github.xuwei-k",
  homepage := Some(url("https://github.com/xuwei-k/unused-proto")),
  licenses := List(
    "MIT License" -> url("https://opensource.org/licenses/mit-license")
  ),
)

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommandAndRemaining("set ThisBuild / useSuperShell := false"),
  releaseStepCommandAndRemaining("publishSigned"),
  releaseStepCommandAndRemaining("set ThisBuild / useSuperShell := true"),
  releaseStepCommandAndRemaining("sonatypeBundleRelease"),
  setNextVersion,
  commitNextVersion,
  pushChanges
)

commonSettings

publish / skip := true

// for scala-steward
val scalapb = "com.thesamet.scalapb" %% "scalapb-runtime" % "0.11.13" % "runtime"

libraryDependencies += scalapb

lazy val plugin = project
  .in(file("plugin"))
  .enablePlugins(ScriptedPlugin)
  .dependsOn(LocalProject("common2_12"))
  .settings(
    commonSettings,
    scalaVersion := Scala212,
    description := "find unused proto sbt plugin",
    scriptedLaunchOpts ++= Seq[(String, String)](
      ("plugin.version", version.value),
      ("scalapb.version", scalapb.revision),
    ).map { case (k, v) =>
      s"-D${k}=${v}"
    },
    scriptedBufferLog := false,
    sbtPlugin := true,
    sbtPluginPublishLegacyMavenStyle := {
      sys.env.isDefinedAt("GITHUB_ACTION") || isSnapshot.value
    },
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb" %% "protoc-bridge" % "0.9.6",
      "com.google.protobuf" % "protobuf-java" % "3.23.3",
    ),
    addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.6"),
    name := "unused-proto-plugin",
    moduleName := "unused-proto-plugin",
  )

lazy val common = projectMatrix
  .in(file("common"))
  .settings(
    commonSettings,
    Compile / sourceGenerators += task {
      val dir = (Compile / sourceManaged).value
      val className = "UnusedProtoBuildInfo"
      val f = dir / "unused_proto" / s"${className}.scala"
      IO.write(
        f,
        Seq(
          "package unused_proto",
          "",
          s"object $className {",
          s"""  def version: String = "${version.value}"""",
          "}",
        ).mkString("", "\n", "\n")
      )
      Seq(f)
    },
    name := "unused-proto-common",
    description := "unused-proto common sources",
  )
  .defaultAxes(VirtualAxis.jvm)
  .jvmPlatform(Seq(Scala212, Scala3))

lazy val core = project
  .in(file("core"))
  .settings(
    commonSettings,
    scalaVersion := Scala3,
    name := "unused-proto",
    description := "find unused proto",
    libraryDependencies ++= Seq(
      "io.argonaut" %% "argonaut" % "6.3.8",
      "org.scala-sbt" %% "io" % "1.9.1",
      "org.scalameta" %% "parsers" % "4.7.8" cross CrossVersion.for3Use2_13,
    )
  )
  .dependsOn(LocalProject("common3"))

ThisBuild / scalafixDependencies += "com.github.xuwei-k" %% "scalafix-rules" % "0.3.0"
ThisBuild / semanticdbVersion := "4.7.8"
ThisBuild / semanticdbEnabled := true
ThisBuild / scalafixOnCompile := true
ThisBuild / scalafmtOnCompile := true

scalaVersion := Scala212
