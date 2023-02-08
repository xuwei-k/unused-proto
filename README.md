# unused proto

find unused proto message, enum and rpc in your sbt project.

## `project/plugins.sbt`

```scala
addSbtPlugin("com.github.xuwei-k" % "unused-proto-plugin" % "latest version")
```

## `build.sbt`

use `++=` or `+=` insted of `:=`.

```diff
- Compile / PB.targets := Seq(
+ Compile / PB.targets ++= Seq[protocbridge.Target](
```

## sbt shell

```
> unusedProtoWarn
```
