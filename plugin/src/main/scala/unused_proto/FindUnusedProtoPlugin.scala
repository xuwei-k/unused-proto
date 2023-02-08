package unused_proto

import java.nio.charset.StandardCharsets
import sbt.Keys.*
import sbt.*
import sbtprotoc.ProtocPlugin.autoImport.PB
import scala.collection.concurrent.TrieMap
import unused_proto.JsonFormatInstances.*

object FindUnusedProtoPlugin extends AutoPlugin {
  object autoImport {
    val unusedProtoInfoAll = taskKey[ProtoValues[UnusedProtoInput.Def]]("aggregated proto info")
    val unusedProtoExternalProtoAll = taskKey[List[String]]("")
    val unusedProtoOutput = settingKey[File]("output file")
    val unusedProto = taskKey[UnusedProtoOutput]("analyze usage in your project scala code by scalameta")
    val unusedProtoWarn = taskKey[Unit]("print unused proto message, enum and rpc")
    val unusedProtoInput = taskKey[UnusedProtoInput]("")
  }

  import autoImport.*

  override def trigger: PluginTrigger = allRequirements

  // https://github.com/scala/bug/issues/11284
  private[this] type AsComparable[A] = A => Comparable[? >: A]

  private[this] implicit def orderingFromJavaComparable[A](implicit asComparable: AsComparable[A]): Ordering[A] =
    (x: A, y: A) => asComparable(x).compareTo(y)

  private[this] val sbtLauncher: Def.Initialize[Task[File]] = Def.task {
    val Seq(launcher) = (LocalRootProject / dependencyResolution).value
      .retrieve(
        dependencyId = "org.scala-sbt" % "sbt-launch" % (LocalRootProject / unusedProto / sbtVersion).value,
        scalaModuleInfo = None,
        retrieveDirectory = (ThisBuild / csrCacheDirectory).value,
        log = streams.value.log
      )
      .left
      .map(e => throw e.resolveException)
      .merge
      .distinct
    launcher
  }

  // avoid extraProjects and derivedProjects
  // https://github.com/sbt/sbt/issues/6860
  // https://github.com/sbt/sbt/issues/4947
  private[this] def run(
    base: File,
    config: UnusedProtoInput,
    launcher: File,
    forkOptions: ForkOptions,
    logLevel: String,
  ): Int = {
    val buildSbt =
      s"""|name := "find-unused-proto-runner"
          |logLevel := Level.${logLevel}
          |autoScalaLibrary := false
          |run / fork := true
          |libraryDependencies ++= Seq(
          |  "com.github.xuwei-k" % "unused-proto_3" % "${UnusedProtoBuildInfo.version}"
          |)
          |Compile / sources := Nil
          |""".stripMargin

    IO.withTemporaryDirectory { dir =>
      val forkOpt = forkOptions.withWorkingDirectory(dir)
      val in = dir / "in.json"
      IO.write(dir / "build.sbt", buildSbt.getBytes(StandardCharsets.UTF_8))
      IO.write(in, config.toJsonString.getBytes(StandardCharsets.UTF_8))
      Fork.java.apply(
        forkOpt,
        Seq(
          "-jar",
          launcher.getCanonicalPath,
          Seq(
            "runMain",
            "unused_proto.FindUnusedProto",
            s"--input=${in.getCanonicalPath}"
          ).mkString(" ")
        )
      )
    }
  }

  private[this] val protoProjects: Def.Initialize[Task[List[LocalProject]]] = Def.task {
    val s = state.value
    val extracted = Project.extract(s)
    val currentBuildUri = extracted.currentRef.build
    extracted.structure.units
      .apply(currentBuildUri)
      .defined
      .values
      .filter(
        _.autoPlugins.contains(CollectProtoInfoPlugin)
      )
      .toList
      .map(p => LocalProject(p.id))
  }

  override def buildSettings: Seq[Def.Setting[?]] = Seq(
    LocalRootProject / unusedProtoOutput := (LocalRootProject / target).value / "unused_proto.json",
    LocalRootProject / unusedProtoExternalProtoAll := Def.taskDyn {
      val base = (LocalRootProject / baseDirectory).value
      val log = state.value.log
      Def.taskDyn {
        protoProjects
          .map(
            _.map(_ / PB.externalSourcePath).join.map(
              _.flatMap(f =>
                IO.relativize(base = base, file = f).orElse {
                  log.warn(s"invalid path ${f.getCanonicalPath}")
                  None
                }
              ).toList
            )
          )
          .value
      }
    }.value,
    LocalRootProject / unusedProtoInput := Def.taskDyn {
      val s = state.value
      val extracted = Project.extract(s)
      val currentBuildUri = extracted.currentRef.build
      val projects = extracted.structure.units.apply(currentBuildUri).defined.values.toList
      val baseDir = (LocalRootProject / baseDirectory).value
      val sourcesTask: Def.Initialize[Task[Seq[File]]] = projects.map { p =>
        LocalProject(p.id) / Compile / unmanagedSources
      }.join.map(_.flatten)
      val all = (LocalRootProject / unusedProtoInfoAll).value
      val externalProtoPaths = (LocalRootProject / unusedProtoExternalProtoAll).value
      val dialect = (LocalRootProject / scalaBinaryVersion).value match {
        case "2.10" =>
          Dialect.Scala210
        case "2.11" =>
          Dialect.Scala211
        case "2.12" =>
          if (scalacOptions.value.contains("-Xsource:3")) {
            Dialect.Scala212Source3
          } else {
            Dialect.Scala212
          }
        case "2.13" =>
          if (scalacOptions.value.contains("-Xsource:3")) {
            Dialect.Scala213Source3
          } else {
            Dialect.Scala213
          }
        case "3" =>
          Dialect.Scala3
        case _ =>
          Dialect.Scala213Source3
      }
      sourcesTask.map { files =>
        UnusedProtoInput(
          baseDirectory = (LocalRootProject / baseDirectory).value.getCanonicalPath,
          externalProtoPaths = externalProtoPaths,
          scalaFiles = files
            .filter(_.getName.endsWith(".scala"))
            .flatMap { f =>
              IO.relativize(baseDir, f).orElse {
                s.log.warn(s"invalid path ${f.getCanonicalFile}")
                None
              }
            }
            .toList,
          protoInfo = all,
          output = (LocalRootProject / unusedProtoOutput).value.getCanonicalPath,
          dialect = dialect,
          git = true
        )
      }
    }.value,
    LocalRootProject / unusedProto / logLevel := Level.Warn,
    LocalRootProject / unusedProtoInfoAll := Def.taskDyn {
      def removeDuplicateByName(xs: List[UnusedProtoInput.Def]): List[UnusedProtoInput.Def] = {
        xs.groupBy(_.name)
          .collect { case (_, List(x)) =>
            x
          }
          .toList
      }

      protoProjects.map(_.map(_ / CollectProtoInfoPlugin.autoImport.unusedProtoInfo).join).value.map {
        (values: Seq[ProtoValues[UnusedProtoInput.Def]]) =>
          val result = values.foldLeft(ProtoValues.empty[UnusedProtoInput.Def]) { (x, y) =>
            ProtoValues(
              methods = x.methods ++ y.methods,
              messages = x.messages ++ y.messages,
              enums = x.enums ++ y.enums
            )
          }

          new ProtoValues(
            methods = removeDuplicateByName(result.methods),
            messages = removeDuplicateByName(result.messages),
            enums = removeDuplicateByName(result.enums)
          ) {
            override def toString: String = (this: ProtoValues[UnusedProtoInput.Def]).toJsonString
          }
      }
    }.value,
    LocalRootProject / unusedProto / forkOptions := ForkOptions(),
    LocalRootProject / unusedProto := {
      val s = state.value
      val conf = (LocalRootProject / unusedProtoInput).value
      assert(conf.scalaFiles.nonEmpty)
      s.log.info(s"scala file count = ${conf.scalaFiles.size}")
      s.log.info(s"method count = ${conf.protoInfo.methods.size}")
      s.log.info(s"message count = ${conf.protoInfo.messages.size}")
      s.log.info(s"enum count = ${conf.protoInfo.enums.size}")
      run(
        base = (LocalRootProject / baseDirectory).value,
        config = conf,
        launcher = sbtLauncher.value,
        forkOptions = (LocalRootProject / unusedProto / forkOptions).value,
        logLevel = {
          val x = (LocalRootProject / unusedProto / logLevel).value.toString
          s"${x.head.toUpper}${x.tail}"
        }
      ) match {
        case 0 =>
          val x = decodeFromJsonFile[UnusedProtoOutput](new File(conf.output))
          new UnusedProtoOutput(
            values = x.values,
            log = x.log,
          ) {
            override def toString: String = (this: UnusedProtoOutput).toJsonString
          }
        case exitCode =>
          sys.error(s"${unusedProto.key.label} failed ${exitCode}")
      }
    },
    LocalRootProject / unusedProtoWarn := {
      val log = state.value.log
      val files = TrieMap.empty[String, List[String]]
      def printResult(key: String, values: Seq[UnusedProtoOutput.Result]): Unit = {
        if (values.isEmpty) {
          log.info(s"not found unused ${key}")
        } else {
          values.foreach { x =>
            val f = file(x.path)
            val lines = files.getOrElseUpdate(x.path, IO.readLines(f))
            val value = Seq[String](
              {
                val base =
                  s"${f.getCanonicalPath}:${x.location.startLine}:${x.location.startColumn}: unused ${key}."
                x.gitInfo.fold(base)(g => s"${base} last change date is ${g.date}. commit = ${g.commit}")
              },
              lines(x.location.startLine),
              if (x.location.endLine.isDefined) {
                List.fill(x.location.startColumn)(" ").mkString + "^"
              } else {
                (List.fill(x.location.startColumn)(" ") ++ List.fill(
                  x.location.endColumn - x.location.startColumn
                )("^")).mkString
              }
            ).mkString("\n")
            log.warn(value)
          }
        }
      }
      val result = (LocalRootProject / unusedProto).value
      printResult("message", result.values.messages)
      printResult("enum", result.values.enums)
      printResult("method", result.values.methods)
      files.clear()
      log.warn(result.log.warnings.mkString("\n"))
    }
  )
}
