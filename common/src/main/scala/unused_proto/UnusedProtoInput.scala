package unused_proto

import unused_proto.UnusedProtoInput.Def

final case class UnusedProtoInput(
  baseDirectory: String,
  externalProtoPaths: List[String],
  scalaFiles: List[String],
  protoInfo: ProtoValues[Def],
  output: String,
  dialect: Dialect,
  git: Boolean
)

object UnusedProtoInput {
  val keys = ("base", "external_proto_paths", "scala_files", "proto_info", "output", "dialect", "git")

  case class Def(name: String, location: Location, fileName: String)
  object Def {
    val keys = ("name", "location", "file_name")
  }

}
