package unused_proto

import com.google.protobuf.DescriptorProtos.FileDescriptorProto
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto
import com.google.protobuf.DescriptorProtos.SourceCodeInfo
import com.google.protobuf.compiler.PluginProtos.*
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse.Feature.FEATURE_PROTO3_OPTIONAL
import java.nio.file.Files
import java.util.Collections
import protocbridge.ProtocCodeGenerator
import sbt.io.syntax.*
import scala.jdk.CollectionConverters.*
import unused_proto.UnusedProtoInput.Def

case class GetProtoInfo(private val outputFile: File) extends ProtocCodeGenerator {
  override def run(req: Array[Byte]): Array[Byte] =
    run(CodeGeneratorRequest.parseFrom(req)).toByteArray

  private[this] def run(req: CodeGeneratorRequest): CodeGeneratorResponse = {
    val toGenerate = req.getFileToGenerateList.asScala.toSet
    val files = req.getProtoFileList.asScala.filter(p => toGenerate.contains(p.getName))
    import JsonFormatInstances.*
    val names = run0(files).toJsonString
    Files.write(outputFile.toPath, Collections.singletonList(names))
    // return empty response.
    // because this protoc-plugin does not generate any source code
    CodeGeneratorResponse.newBuilder().setSupportedFeatures(FEATURE_PROTO3_OPTIONAL.getNumber).build()
  }

  private[this] def locationFromProto(p: SourceCodeInfo.Location): Option[Location] =
    PartialFunction.condOpt(p.getSpanList.asScala.toList) {
      case List(startLine, startColumn, endLine, endColumn) =>
        Location(
          startLine = startLine,
          startColumn = startColumn,
          endLine = Some(endLine),
          endColumn = endColumn
        )
      case List(startLine, startColumn, endColumn) =>
        Location(
          startLine = startLine,
          startColumn = startColumn,
          endLine = None,
          endColumn = endColumn
        )
    }

  private case class MethodIndex(service: Int, method: Int)

  private[this] def run0(files: collection.Seq[FileDescriptorProto]): ProtoValues[Def] = {
    ProtoValues(
      methods = files.flatMap { f =>
        val locations = f.getSourceCodeInfo.getLocationList.asScala.flatMap { location =>
          location.getPathList.asScala.map(_.toInt).toList match {
            case List(
                  FileDescriptorProto.SERVICE_FIELD_NUMBER,
                  serviceIndex,
                  ServiceDescriptorProto.METHOD_FIELD_NUMBER,
                  methodIndex
                ) =>
              val i = MethodIndex(service = serviceIndex, method = methodIndex)
              locationFromProto(location).map(i -> _)
            case _ =>
              None
          }
        }.toMap

        f.getServiceList.asScala.zipWithIndex.flatMap { case (service, serviceIndex) =>
          service.getMethodList.asScala.zipWithIndex.map { case (method, methodIndex) =>
            UnusedProtoInput.Def(
              name = method.getName,
              location = locations(MethodIndex(service = serviceIndex, method = methodIndex)),
              fileName = f.getName,
            )
          }
        }
      }.toList,
      messages = files.flatMap { f =>
        val locations = f.getSourceCodeInfo.getLocationList.asScala.flatMap { location =>
          location.getPathList.asScala.map(_.toInt).toList match {
            case List(
                  FileDescriptorProto.MESSAGE_TYPE_FIELD_NUMBER,
                  index,
                ) =>
              locationFromProto(location).map(index -> _)
            case _ =>
              None
          }
        }.toMap
        f.getMessageTypeList.asScala.zipWithIndex.map { case (message, index) =>
          UnusedProtoInput.Def(
            name = message.getName,
            location = locations(index),
            fileName = f.getName,
          )
        }
      }.toList,
      enums = files.flatMap { f =>
        val locations = f.getSourceCodeInfo.getLocationList.asScala.flatMap { location =>
          location.getPathList.asScala.map(_.toInt).toList match {
            case List(
                  FileDescriptorProto.ENUM_TYPE_FIELD_NUMBER,
                  index,
                ) =>
              locationFromProto(location).map(index -> _)
            case _ =>
              None
          }
        }.toMap
        f.getEnumTypeList.asScala.zipWithIndex.map { case (enumValue, index) =>
          UnusedProtoInput.Def(
            name = enumValue.getName,
            location = locations(index),
            fileName = f.getName,
          )
        }
      }.toList,
    )
  }
}
