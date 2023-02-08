package unused_proto

case class Location(startLine: Int, startColumn: Int, endLine: Option[Int], endColumn: Int) {
  def getEndLine: Int = endLine.getOrElse(startLine)
}

object Location {
  val keys = ("start_line", "start_column", "end_line", "end_column")
}
