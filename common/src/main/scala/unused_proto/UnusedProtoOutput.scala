package unused_proto

import java.time.ZonedDateTime
import unused_proto.UnusedProtoOutput.Result

case class UnusedProtoOutput(
  values: ProtoValues[Result],
  log: UnusedProtoOutput.Log,
)

object UnusedProtoOutput {
  val keys = ("values", "log")

  case class GitInfo(date: ZonedDateTime, commit: String)
  object GitInfo {
    val keys = ("path", "date")
  }
  case class Result(name: String, path: String, location: Location, gitInfo: Option[GitInfo])
  object Result {
    val keys = ("name", "path", "location", "git_info")
  }

  case class Log(warnings: List[String])
  object Log {
    val keys = Tuple1("warnings")
  }
}
