package unused_proto

import sbt.*
import sbt.Keys.*
import sbtprotoc.ProtocPlugin
import sbtprotoc.ProtocPlugin.autoImport.PB

object CollectProtoInfoPlugin extends AutoPlugin {
  object autoImport {
    val unusedProtoInfo = taskKey[ProtoValues[UnusedProtoInput.Def]]("input proto file info each project")
  }

  import autoImport.*

  override def trigger: PluginTrigger = allRequirements
  override def requires: Plugins = ProtocPlugin

  private val protoInfoFile = Def.setting[File] {
    target.value / s"proto-info-${name.value}.json"
  }

  override def projectSettings: Seq[Def.Setting[?]] = Def.settings(
    unusedProtoInfo := {
      val _ = (Compile / PB.generate).value
      val f = protoInfoFile.value
      val log = state.value.log
      if (f.isFile) {
        val json = sjsonnew.support.scalajson.unsafe.Parser.parseFromFile(f).get
        import JsonFormatInstances.*
        JsonFormatInstances.decodeFromJson[ProtoValues[UnusedProtoInput.Def]](json)
      } else {
        log.info(s"not found proto files in ${name.value}")
        ProtoValues.empty
      }
    },
    Compile / PB.targets ++= Seq[protocbridge.Target](
      GetProtoInfo(protoInfoFile.value) -> ((Compile / sourceManaged).value / "unused_proto"),
    )
  )
}
