import sbtcompat.PluginCompat._

enablePlugins(ProtobufPlugin)

scalaVersion := "2.12.21"

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

InputKey[Unit]("checkRuntime") := Def.uncached {
  val expected = sbt.complete.DefaultParsers.spaceDelimited("runtime").parsed.head
  val files = ((ProtobufConfig / javaSource).value ** "*.java").get()
  assert(files.nonEmpty)
  assert(files.forall(f => IO.read(f).contains("com.google.protobuf." + expected)))
}

TaskKey[Unit]("checkExcluded") := Def.uncached {
  val files = (ProtobufConfig / protobufGenerate).value
  assert(files.size == 1, s"Expected only root.proto to be compiled: $files")
  assert(!((ProtobufConfig / javaSource).value / "test" / "ExtraOuterClass.java").exists())
}

TaskKey[Unit]("checkEmptyTargets") := Def.uncached {
  assert((ProtobufConfig / protobufGenerate).value.isEmpty)
}
