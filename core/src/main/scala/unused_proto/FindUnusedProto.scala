package unused_proto

import argonaut.CodecJson
import argonaut.DecodeJson
import argonaut.EncodeJson
import argonaut.JsonParser
import argonaut.PrettyParams
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.stream.Collectors
import sbt.io.IO
import scala.jdk.CollectionConverters.*
import scala.meta.Source
import scala.meta.Type
import scala.meta.inputs.Input
import scala.meta.parsers.Parse
import scala.meta.transversers.XtensionCollectionLikeUI
import scala.sys.process.Process
import scala.util.chaining.*
import unused_proto.UnusedProtoOutput.GitInfo
import unused_proto.UnusedProtoOutput.Result

object FindUnusedProto {
  private implicit val locationCodec: CodecJson[Location] =
    CodecJson.codec4(Location.apply, Tuple.fromProductTyped[Location])(
      Location.keys._1,
      Location.keys._2,
      Location.keys._3,
      Location.keys._4,
    )

  private implicit def protoValuesEncoder[A: EncodeJson]: EncodeJson[ProtoValues[A]] =
    EncodeJson.jencode3L(Tuple.fromProductTyped[ProtoValues[A]])(
      ProtoValues.keys._1,
      ProtoValues.keys._2,
      ProtoValues.keys._3,
    )
  private implicit def protoValuesDecoder[A: DecodeJson]: DecodeJson[ProtoValues[A]] =
    DecodeJson.jdecode3L(ProtoValues.apply[A])(
      ProtoValues.keys._1,
      ProtoValues.keys._2,
      ProtoValues.keys._3,
    )

  private implicit val decoderUnusedProtoInput: DecodeJson[UnusedProtoInput] = {
    implicit val defDecoder: DecodeJson[UnusedProtoInput.Def] =
      DecodeJson.jdecode3L(UnusedProtoInput.Def.apply)(
        UnusedProtoInput.Def.keys._1,
        UnusedProtoInput.Def.keys._2,
        UnusedProtoInput.Def.keys._3,
      )

    implicit val dialectDecoder: DecodeJson[Dialect] =
      implicitly[DecodeJson[String]].map(Dialect.map)

    DecodeJson.jdecode8L(UnusedProtoInput.apply)(
      UnusedProtoInput.keys._1,
      UnusedProtoInput.keys._2,
      UnusedProtoInput.keys._3,
      UnusedProtoInput.keys._4,
      UnusedProtoInput.keys._5,
      UnusedProtoInput.keys._6,
      UnusedProtoInput.keys._7,
      UnusedProtoInput.keys._8,
    )
  }

  private implicit val unusedProtoOutputEncoder: EncodeJson[UnusedProtoOutput] = {
    implicit val zonedDateTimeEncoder: EncodeJson[ZonedDateTime] =
      implicitly[EncodeJson[String]].contramap(_.format(DateTimeFormatter.ISO_DATE_TIME))

    implicit val gitInfoEncoder: EncodeJson[GitInfo] =
      EncodeJson.jencode2L(Tuple.fromProductTyped[GitInfo])(
        GitInfo.keys._1,
        GitInfo.keys._2,
      )

    implicit val resultEncoder: EncodeJson[Result] =
      EncodeJson.jencode4L(Tuple.fromProductTyped[Result])(
        Result.keys._1,
        Result.keys._2,
        Result.keys._3,
        Result.keys._4,
      )

    implicit val logEncoder: EncodeJson[UnusedProtoOutput.Log] =
      EncodeJson.jencode1L((_: UnusedProtoOutput.Log).warnings)(
        UnusedProtoOutput.Log.keys._1,
      )

    EncodeJson.jencode2L(Tuple.fromProductTyped[UnusedProtoOutput])(
      UnusedProtoOutput.keys._1,
      UnusedProtoOutput.keys._2
    )
  }

  private def jsonToInput(json: String): UnusedProtoInput = {
    JsonParser.parse(json).flatMap(_.as[UnusedProtoInput].toEither.left.map(_._1)).fold(sys.error, identity)
  }

  private def scalametaDialect(d: unused_proto.Dialect): scala.meta.Dialect =
    d match {
      case Dialect.Scala210 =>
        scala.meta.dialects.Scala210
      case Dialect.Scala211 =>
        scala.meta.dialects.Scala211
      case Dialect.Scala212 =>
        scala.meta.dialects.Scala212
      case Dialect.Scala213 =>
        scala.meta.dialects.Scala213
      case Dialect.Scala212Source3 =>
        scala.meta.dialects.Scala212Source3
      case Dialect.Scala213Source3 =>
        scala.meta.dialects.Scala213Source3
      case Dialect.Scala3 =>
        scala.meta.dialects.Scala3
    }

  def main(args: Array[String]): Unit = try {
    val in = {
      val key = "--input="
      val fileName = args.collectFirst {
        case a if a.startsWith(key) => a.drop(key.length)
      }.getOrElse(throw new IllegalArgumentException(s"unspecified ${key}"))
      val f = new File(fileName)
      new String(Files.readAllBytes(f.toPath), StandardCharsets.UTF_8)
    }
    val conf = jsonToInput(in)
    assert(conf.scalaFiles.nonEmpty, "scala files is empty!?")

    val namesInScala = conf.scalaFiles.flatMap { file =>
      val input = Input.File(new File(conf.baseDirectory, file))
      val tree = implicitly[Parse[Source]].apply(input, scalametaDialect(conf.dialect)).get
      tree.collect {
        case x: Type.Name =>
          x.value
        case x: scala.meta.Name =>
          x.value
      }
    }.groupBy(identity).map { case (k, v) => k -> v.size }

    def sortResult(values: List[Result]): List[Result] =
      values.sortBy { x =>
        (x.gitInfo.map(_.date), x.path, x.location.startLine, x.location.startColumn)
      }

    val (warn1, methods) = unusedMethods(conf, namesInScala)
    val (warn2, messages) = unusedEnumOrMessage(
      values = conf.protoInfo.messages,
      namesInScala = namesInScala,
      conf = conf,
    )
    val (warn3, enums) =
      unusedEnumOrMessage(
        values = conf.protoInfo.enums,
        namesInScala = namesInScala,
        conf = conf,
      )
    val result = UnusedProtoOutput(
      ProtoValues(
        methods = sortResult(methods),
        messages = sortResult(messages),
        enums = sortResult(enums),
      ),
      log = UnusedProtoOutput.Log(
        warnings = warn1 ++ warn2 ++ warn3
      )
    )
    val jsonString =
      implicitly[EncodeJson[UnusedProtoOutput]].encode(result).pretty(PrettyParams.spaces2)
    Files.write(
      new File(conf.output).toPath,
      jsonString.getBytes(StandardCharsets.UTF_8)
    )
  } catch {
    case e: Throwable =>
      e.printStackTrace()
      throw e
  }

  private def findProtoFile(conf: UnusedProtoInput, f: UnusedProtoInput.Def): Either[String, List[File]] = {
    val all = conf.protoDirectories.map(dir => new File(conf.baseDirectory, dir)).filter(_.isDirectory)
    all.flatMap { dir =>
      Files
        .find(
          dir.toPath,
          32,
          { (path, attributes) =>
            attributes.isRegularFile && (path.toFile.getName == f.fileName.split('/').last) && {
              val lines = Files.readAllLines(path).asScala.lift
              lines(f.location.startLine).exists(_ contains f.name)
            }
          },
          FileVisitOption.FOLLOW_LINKS,
        )
        .collect(Collectors.toList[Path]())
        .asScala
        .toList
    } match {
      case Nil =>
        Left(s"not found ${f.name} ${f.fileName}")
      case values =>
        Right(values.map(_.toFile))
    }
  }

  private def unusedEnumOrMessage(
    values: List[UnusedProtoInput.Def],
    namesInScala: Map[String, Int],
    conf: UnusedProtoInput,
  ): (List[String], List[Result]) = {
    values
      .filterNot(x => namesInScala.contains(x.name))
      .map { value =>
        val name = value.name
        findProtoFile(conf = conf, f = value).map { files =>
          files.map { file =>
            val info = gitInfo(
              file = file,
              startLine = value.location.startLine,
              endLine = value.location.getEndLine,
              conf = conf,
            )
            val path =
              IO.relativize(new File(conf.baseDirectory), file.getCanonicalFile)
                .getOrElse(sys.error(s"invalid path ${file.getCanonicalPath}"))
            Result(name = name, path = path, location = value.location, gitInfo = info)
          }
        }
      }
      .partitionMap(identity)
      .pipe { x =>
        x.copy(_2 = x._2.flatten)
      }
  }

  private def gitInfo(file: File, startLine: Int, endLine: Int, conf: UnusedProtoInput): Option[GitInfo] = {
    val base = new File(conf.baseDirectory)
    val notExternal = conf.externalProtoPaths.forall { externalDir =>
      IO.relativize(
        base = new File(base, externalDir),
        file = file,
      ).isEmpty
    }
    Option.when(conf.git && new File(base, ".git").isDirectory && notExternal) {
      val commitDateTime = Process(
        Seq(
          "git",
          "log",
          "-1",
          "--format=%cd",
          "--date=iso8601-strict",
          s"-L${startLine},${endLine}:${file.getCanonicalPath}"
        ),
        Some(base)
      ).lazyLines_!.head
      val commitHash = Process(
        Seq(
          "git",
          "log",
          "-1",
          "--format=%H",
          s"-L${startLine},${endLine}:${file.getCanonicalPath}"
        ),
        Some(base)
      ).lazyLines_!.head

      GitInfo(
        date = ZonedDateTime.parse(commitDateTime),
        commit = commitHash
      )
    }
  }

  private def unusedMethods(conf: UnusedProtoInput, namesInScala: Map[String, Int]): (List[String], List[Result]) = {
    val methods = conf.protoInfo.methods
      .filter(_.name.nonEmpty)
      .map { method =>
        val name = method.name
        val lower = s"${name.head.toLower}${name.tail}"
        lower -> method
      }
      .toMap
    val nameAll = {
      namesInScala ++ methods
        .filterNot((key, _) => namesInScala.isDefinedAt(key))
        .view
        .mapValues(Function.const(0))
        .toMap
    }
    nameAll.filter { case (name, count) =>
      methods.isDefinedAt(name) && count <= 1
    }.keys.toList.map { methodNameInScala =>
      val method = methods(methodNameInScala)
      val methodNameInProto = method.name
      findProtoFile(conf = conf, f = method).map { files =>
        files.map { file =>
          val path =
            IO.relativize(new File(conf.baseDirectory), file.getCanonicalFile)
              .getOrElse(sys.error(s"invalid path ${file.getCanonicalPath}"))
          val info = gitInfo(
            file = file,
            startLine = method.location.startLine,
            endLine = method.location.getEndLine,
            conf = conf,
          )
          Result(name = methodNameInProto, path = path, location = method.location, gitInfo = info)
        }
      }
    }.partitionMap(identity).pipe { x =>
      x.copy(_2 = x._2.flatten)
    }
  }

}
