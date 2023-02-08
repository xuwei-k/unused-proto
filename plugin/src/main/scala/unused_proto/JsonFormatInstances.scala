package unused_proto

import java.io.File
import sjsonnew.Builder
import sjsonnew.JsonFormat
import sjsonnew.JsonReader
import sjsonnew.JsonWriter
import sjsonnew.Unbuilder
import sjsonnew.shaded.scalajson.ast.unsafe.JValue
import sjsonnew.support.scalajson.unsafe.PrettyPrinter

private[unused_proto] object JsonFormatInstances {

  def decodeFromJsonFile[A: JsonReader](file: File): A = {
    val json = sjsonnew.support.scalajson.unsafe.Parser.parseFromFile(file).get
    decodeFromJson[A](json)
  }

  def decodeFromJson[A](json: JValue)(implicit r: JsonReader[A]): A = {
    val unbuilder = new sjsonnew.Unbuilder(sjsonnew.support.scalajson.unsafe.Converter.facade)
    r.read(Some(json), unbuilder)
  }

  implicit class JsonOps[A](private val self: A) extends AnyVal {
    def toJsonString(implicit writer: JsonWriter[A]): String = {
      val builder = new Builder(sjsonnew.support.scalajson.unsafe.Converter.facade)
      writer.write(self, builder)
      PrettyPrinter.apply(
        builder.result.getOrElse(sys.error("invalid json"))
      )
    }
  }

  private[this] implicit val locationInstance: JsonFormat[Location] = {
    import sjsonnew.BasicJsonProtocol.*

    caseClass4(Location.apply, Location.unapply)(
      Location.keys._1,
      Location.keys._2,
      Location.keys._3,
      Location.keys._4,
    )
  }

  implicit val defInstance: JsonFormat[UnusedProtoInput.Def] = {
    import sjsonnew.BasicJsonProtocol.*

    caseClass3(UnusedProtoInput.Def.apply, UnusedProtoInput.Def.unapply)(
      UnusedProtoInput.Def.keys._1,
      UnusedProtoInput.Def.keys._2,
      UnusedProtoInput.Def.keys._3,
    )
  }

  implicit def protoValuesInstances[A: JsonFormat]: JsonFormat[ProtoValues[A]] = {
    import sjsonnew.BasicJsonProtocol.*

    caseClass3(ProtoValues.apply[A], ProtoValues.unapply[A])(
      ProtoValues.keys._1,
      ProtoValues.keys._2,
      ProtoValues.keys._3,
    )
  }

  private[this] def convertJsonFormat[A, B](f: JsonFormat[A])(f1: A => B, f2: B => A): JsonFormat[B] =
    new JsonFormat[B] {
      override def write[J](obj: B, builder: Builder[J]) =
        f.write(f2(obj), builder)

      override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]) =
        f1(f.read(jsOpt, unbuilder))
    }

  private[unused_proto] implicit val unusedProtoInputInstance: JsonFormat[UnusedProtoInput] = {
    import sjsonnew.BasicJsonProtocol.*

    implicit val dialect: JsonFormat[Dialect] =
      convertJsonFormat(implicitly[JsonFormat[String]])(Dialect.map, _.value)

    caseClass7(UnusedProtoInput.apply, UnusedProtoInput.unapply)(
      UnusedProtoInput.keys._1,
      UnusedProtoInput.keys._2,
      UnusedProtoInput.keys._3,
      UnusedProtoInput.keys._4,
      UnusedProtoInput.keys._5,
      UnusedProtoInput.keys._6,
      UnusedProtoInput.keys._7,
    )
  }

  private[unused_proto] implicit val unusedProtoOutputInstance: JsonFormat[UnusedProtoOutput] = {
    import sjsonnew.BasicJsonProtocol.*

    implicit val gitInto: JsonFormat[UnusedProtoOutput.GitInfo] =
      caseClass2(UnusedProtoOutput.GitInfo.apply, UnusedProtoOutput.GitInfo.unapply)(
        UnusedProtoOutput.GitInfo.keys._1,
        UnusedProtoOutput.GitInfo.keys._2,
      )

    implicit val result: JsonFormat[UnusedProtoOutput.Result] =
      caseClass4(UnusedProtoOutput.Result.apply, UnusedProtoOutput.Result.unapply)(
        UnusedProtoOutput.Result.keys._1,
        UnusedProtoOutput.Result.keys._2,
        UnusedProtoOutput.Result.keys._3,
        UnusedProtoOutput.Result.keys._4,
      )

    implicit val log: JsonFormat[UnusedProtoOutput.Log] =
      caseClass1(UnusedProtoOutput.Log.apply, UnusedProtoOutput.Log.unapply)(
        UnusedProtoOutput.Log.keys._1,
      )

    caseClass2(UnusedProtoOutput.apply, UnusedProtoOutput.unapply)(
      UnusedProtoOutput.keys._1,
      UnusedProtoOutput.keys._2,
    )
  }

}
