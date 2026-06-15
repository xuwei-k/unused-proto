import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations.*

def sbt2 = "2.0.0"

def Scala212 = "2.12.21"
val Scala3: String = scala_version_from_sbt_version.ScalaVersionFromSbtVersion(sbt2)

val commonSettings = Def.settings(
  publishTo := (if (isSnapshot.value) None else localStaging.value),
  Compile / unmanagedResources += (LocalRootProject / baseDirectory).value / "LICENSE.txt",
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
      Seq(
        "-source-links:github://xuwei-k/unused-proto",
        "-revision",
        hash,
      )
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
        "-release:8",
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
  releaseStepCommandAndRemaining("sonaRelease"),
  setNextVersion,
  commitNextVersion,
  pushChanges
)

commonSettings

publish / skip := true

// for scala-steward
val scalapb = "com.thesamet.scalapb" %% "scalapb-runtime" % "0.11.20" % "runtime"

libraryDependencies += scalapb

lazy val plugin = projectMatrix
  .in(file("plugin"))
  .enablePlugins(ScriptedPlugin)
  .defaultAxes(VirtualAxis.jvm)
  .jvmPlatform(Seq(Scala212, Scala3))
  .dependsOn(common)
  .settings(
    commonSettings,
    libraryDependencies += "com.github.xuwei-k" %% "unapply" % "0.2.0",
    addSbtPlugin("com.github.sbt" % "sbt2-compat" % "0.1.0"),
    pluginCrossBuild / sbtVersion := {
      scalaBinaryVersion.value match {
        case "2.12" =>
          sbtVersion.value
        case _ =>
          sbt2
      }
    },
    description := "find unused proto sbt plugin",
    scriptedLaunchOpts ++= Seq[(String, String)](
      ("plugin.version", version.value),
      (
        "scalapb.version", {
          scalaBinaryVersion.value match {
            case "2.12" =>
              scalapb.revision
            case "3" =>
              "1.0.0-alpha.4"
          }
        }
      ),
    ).map { case (k, v) =>
      s"-D${k}=${v}"
    },
    scriptedBufferLog := false,
    sbtPlugin := true,
    sbtPluginPublishLegacyMavenStyle := {
      sys.env.isDefinedAt("GITHUB_ACTION") || isSnapshot.value
    },
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb" %% "protoc-bridge" % "0.9.9",
      "com.google.protobuf" % "protobuf-java" % "3.25.9",
    ),
    libraryDependencies += {
      val v = (pluginCrossBuild / sbtBinaryVersion).value match {
        case "1.0" =>
          "1.0.8"
        case "2" =>
          "1.1.0-RC1"
      }
      Defaults.sbtPluginExtra(
        "com.thesamet" % "sbt-protoc" % v,
        (pluginCrossBuild / sbtBinaryVersion).value,
        (update / scalaBinaryVersion).value,
      )
    },
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
      "io.github.argonaut-io" %% "argonaut" % "6.3.13",
      "org.scala-sbt" %% "io" % "1.12.1",
      "org.scalameta" %% "parsers" % "4.17.0",
    )
  )
  .dependsOn(LocalProject("common3"))

ThisBuild / scalafixDependencies += "com.github.xuwei-k" %% "scalafix-rules" % "0.6.28"
ThisBuild / scalafixOnCompile := true
ThisBuild / scalafmtOnCompile := true

scalaVersion := Scala212
