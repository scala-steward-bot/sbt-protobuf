import sbtcompat.PluginCompat._
import sbtprotobuf.ProtobufTestPlugin.{Keys => PBT}

enablePlugins(ProtobufPlugin, ProtobufTestPlugin)

scalaVersion := "2.12.21"

Compile / unmanagedBase := baseDirectory.value / "dependency-jars"
Test / unmanagedBase := baseDirectory.value / "test-jars"

ProtobufConfig / managedClasspath := Def.uncached((Compile / unmanagedJars).value)
PBT.ProtobufConfig / managedClasspath := Def.uncached((Test / unmanagedJars).value)

def relativePath(base: File, file: File): String =
  IO.relativize(base, file).get.replace(java.io.File.separatorChar, '/')

def installJar(base: File, variant: String, jar: File): Unit = {
  val source = base / "testdata" / variant
  val mappings = (source ** "*").get().filter(_.isFile).map(f => f -> relativePath(source, f))
  IO.createDirectory(jar.getParentFile)
  IO.delete(jar)
  IO.zip(mappings, jar, Some(0L))
}

TaskKey[Unit]("initDependencies") := Def.uncached {
  val base = baseDirectory.value
  installJar(base, "v1", base / "dependency-jars" / "main.jar")
  installJar(base, "other", base / "dependency-jars" / "other.jar")
  installJar(base, "test", base / "test-jars" / "test.jar")
  IO.write((ProtobufConfig / protobufExternalIncludePath).value / "keep.txt", "keep")
}

TaskKey[Unit]("upgradeDependency") := Def.uncached {
  val base = baseDirectory.value
  installJar(base, "v2", base / "dependency-jars" / "main.jar")
  IO.delete((ProtobufConfig / javaSource).value)
}

InputKey[Unit]("checkSchemas") := Def.uncached {
  val expected = sbt.complete.DefaultParsers.spaceDelimited("schema").parsed.toSet
  val unpacked = (ProtobufConfig / protobufUnpackDependencies).value
  val actual = (unpacked.dir ** "*.proto").get().map(relativePath(unpacked.dir, _)).toSet
  val reported = unpacked.files.map(relativePath(unpacked.dir, _)).toSet
  assert(actual == expected, s"Extracted schemas: $actual; expected: $expected")
  assert(reported == expected, s"Reported schemas: $reported; expected: $expected")
  assert(IO.read(unpacked.dir / "keep.txt") == "keep")
  assert(!(unpacked.dir / "ignored.txt").exists())
}

TaskKey[Unit]("checkUpdatedSchema") := Def.uncached {
  val source = (ProtobufConfig / protobufExternalIncludePath).value / "shared.proto"
  assert(IO.read(source).contains("version_two"))
}

TaskKey[Unit]("checkTestSchemas") := Def.uncached {
  val unpacked = (PBT.ProtobufConfig / PBT.protobufUnpackDependencies).value
  val mainDir = (ProtobufConfig / protobufExternalIncludePath).value
  assert(unpacked.dir != mainDir, "Configurations must have separate extraction directories")
  assert(unpacked.files.map(_.getName).toSet == Set("test-only.proto"))
  assert((mainDir / "new.proto").isFile)
}

TaskKey[Unit]("useNewSchema") := Def.uncached {
  val source = baseDirectory.value / "src" / "main" / "protobuf" / "root.proto"
  IO.write(source, IO.read(source).replace("legacy/old.proto", "new.proto").replace("dep.Old", "dep.New"))
}

TaskKey[Unit]("removeOtherDependency") := Def.uncached {
  IO.delete(baseDirectory.value / "dependency-jars" / "other.jar")
}

TaskKey[Unit]("removeAllDependencies") := Def.uncached {
  IO.delete(baseDirectory.value / "dependency-jars")
  IO.delete((ProtobufConfig / javaSource).value)
}

TaskKey[Unit]("checkTestRetained") := Def.uncached {
  val dir = (PBT.ProtobufConfig / PBT.protobufExternalIncludePath).value
  assert((dir / "test-only.proto").isFile, "Main cleanup removed a test dependency")
}
