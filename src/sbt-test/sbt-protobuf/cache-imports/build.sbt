import sbtcompat.PluginCompat._

enablePlugins(ProtobufPlugin)

scalaVersion := "2.12.21"

ProtobufConfig / protobufIncludePaths ++= Def.uncached(Seq(
  baseDirectory.value / "imports",
  baseDirectory.value / "alternate",
))

ProtobufConfig / protobufRunProtoc := Def.uncached {
  val run = (ProtobufConfig / protobufRunProtoc).value
  val count = baseDirectory.value / "runs"
  (args: Seq[String]) => {
    IO.append(count, "run\n")
    run(args)
  }
}

InputKey[Unit]("checkRuns") := Def.uncached {
  val expected = sbt.complete.DefaultParsers.spaceDelimited("count").parsed.head.toInt
  val actual = IO.readLines(baseDirectory.value / "runs").size
  assert(actual == expected, s"Expected $expected protoc runs, got $actual")
}

InputKey[Unit]("checkPackage") := Def.uncached {
  val expected = sbt.complete.DefaultParsers.spaceDelimited("package").parsed.head
  val files = ((ProtobufConfig / javaSource).value ** "*.java").get()
  assert(files.size == 1, s"Imported schemas must not be compiled: $files")
  assert(IO.read(files.head).contains(expected + ".Dep.Payload"))
}

TaskKey[Unit]("touchImport") := Def.uncached {
  val source = baseDirectory.value / "imports" / "dep.proto"
  java.nio.file.Files.setLastModifiedTime(
    source.toPath,
    java.nio.file.attribute.FileTime.fromMillis(source.lastModified() + 10000),
  )
}

TaskKey[Unit]("changeImport") := Def.uncached {
  val source = baseDirectory.value / "imports" / "dep.proto"
  val modified = java.nio.file.Files.getLastModifiedTime(source.toPath)
  IO.write(source, IO.read(source).replace("dep.v1", "dep.v2"))
  java.nio.file.Files.setLastModifiedTime(source.toPath, modified)
}

TaskKey[Unit]("changeTransitiveImport") := Def.uncached {
  val source = baseDirectory.value / "imports" / "nested" / "value.proto"
  IO.write(source, IO.read(source).replace("string value", "bytes value"))
}

TaskKey[Unit]("deleteOutputs") := Def.uncached(IO.delete((ProtobufConfig / javaSource).value))

TaskKey[Unit]("deleteTransitiveImport") := Def.uncached {
  IO.delete(baseDirectory.value / "imports" / "nested" / "value.proto")
}
