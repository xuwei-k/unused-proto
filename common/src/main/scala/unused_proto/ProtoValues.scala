package unused_proto

case class ProtoValues[A](
  methods: List[A],
  messages: List[A],
  enums: List[A],
)

object ProtoValues {
  val keys = ("methods", "messages", "enums")
  def empty[A]: ProtoValues[A] = ProtoValues(Nil, Nil, Nil)
}
