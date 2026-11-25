import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

/**
 * Compile le cœur Rust (`rust/`) pour Android avec cargo-ndk et génère les
 * bindings Kotlin UniFFI. Les deux sorties sont branchées sur chaque variante
 * via l'API `androidComponents` (jniLibs + sources Kotlin générées).
 *
 * Prérequis : rustup, cargo-ndk, NDK installé (ANDROID_NDK_HOME ou SDK/ndk).
 */
class RustAndroidConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val rustDir = rootProject.layout.projectDirectory.dir("rust")
            val abis = (findProperty("apptv.abis") as String? ?: "arm64-v8a,armeabi-v7a,x86_64")
                .split(',').map(String::trim).filter(String::isNotEmpty)
            val cargo = findCargo()
            val sources = files(
                rustDir.file("Cargo.toml"),
                rustDir.file("Cargo.lock"),
                rustDir.file("rust-toolchain.toml"),
                fileTree(rustDir.dir("crates")) { exclude("**/target/**") },
            )

            val androidComponents = extensions.getByType<LibraryAndroidComponentsExtension>()
            androidComponents.onVariants { variant ->
                val name = variant.name.replaceFirstChar(Char::uppercase)
                val ndk = tasks.register<CargoNdkTask>("cargoNdk$name") {
                    group = "rust"
                    description = "Compile xtream-core pour $abis"
                    this.cargo.set(cargo)
                    this.rustDir.set(rustDir.asFile.absolutePath)
                    this.abis.set(abis)
                    this.rustSources.from(sources)
                }
                val bindgen = tasks.register<UniffiBindgenTask>("uniffiBindgen$name") {
                    group = "rust"
                    description = "Génère les bindings Kotlin UniFFI"
                    this.cargo.set(cargo)
                    this.rustDir.set(rustDir.asFile.absolutePath)
                    this.rustSources.from(sources)
                }
                variant.sources.jniLibs?.addGeneratedSourceDirectory(ndk, CargoNdkTask::outputDir)
                variant.sources.kotlin?.addGeneratedSourceDirectory(bindgen, UniffiBindgenTask::outputDir)
            }
        }
    }

    private fun Project.findCargo(): String {
        (findProperty("apptv.cargo") as String?)?.let { return it }
        val home = File(System.getProperty("user.home"), ".cargo/bin/cargo")
        return if (home.exists()) home.absolutePath else "cargo"
    }
}

abstract class CargoNdkTask : DefaultTask() {
    @get:Input abstract val cargo: Property<String>
    @get:Input abstract val rustDir: Property<String>
    @get:Input abstract val abis: ListProperty<String>
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val rustSources: ConfigurableFileCollection
    @get:OutputDirectory abstract val outputDir: DirectoryProperty
    @get:Inject abstract val exec: ExecOperations

    @TaskAction
    fun build() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        val args = buildList {
            add("ndk")
            abis.get().forEach { add("-t"); add(it) }
            add("-P"); add("23")
            add("-o"); add(out.absolutePath)
            add("build"); add("--release"); add("-p"); add("xtream-core")
        }
        exec.exec {
            workingDir = File(rustDir.get())
            commandLine(listOf(cargo.get()) + args)
        }
    }
}

abstract class UniffiBindgenTask : DefaultTask() {
    @get:Input abstract val cargo: Property<String>
    @get:Input abstract val rustDir: Property<String>
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val rustSources: ConfigurableFileCollection
    @get:OutputDirectory abstract val outputDir: DirectoryProperty
    @get:Inject abstract val exec: ExecOperations

    @get:Internal
    val hostLibName: String
        get() {
            val os = System.getProperty("os.name").lowercase()
            return when {
                os.contains("mac") -> "libxtream_core.dylib"
                os.contains("win") -> "xtream_core.dll"
                else -> "libxtream_core.so"
            }
        }

    @TaskAction
    fun generate() {
        val dir = File(rustDir.get())
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        // Build hôte non strippé : les métadonnées UniFFI sont lues dans la bibliothèque.
        exec.exec {
            workingDir = dir
            commandLine(cargo.get(), "build", "-p", "xtream-core")
        }
        exec.exec {
            workingDir = dir
            commandLine(
                cargo.get(), "run", "-p", "uniffi-bindgen", "--",
                "generate",
                "--library", File(dir, "target/debug/$hostLibName").absolutePath,
                "--language", "kotlin",
                "--no-format",
                "--out-dir", out.absolutePath,
            )
        }
    }
}
