import example.Service2Grpc.Service2
import example.BBB
import com.google.protobuf.empty.Empty
import scala.concurrent.Future

object Main {
  val s2 = new Service2 {
    def rpcMethodName2(req: BBB): Future[BBB] = Future.successful(req)
    def rpcMethodName3(req: Empty): Future[Empty] = Future.successful(req)
  }

  s2.rpcMethodName2(BBB())
}
