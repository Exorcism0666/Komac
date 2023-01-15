package data.shared

import Errors
import com.github.ajalt.mordant.rendering.TextColors.brightGreen
import com.github.ajalt.mordant.rendering.TextColors.brightRed
import com.github.ajalt.mordant.rendering.TextColors.brightWhite
import com.github.ajalt.mordant.rendering.TextColors.cyan
import com.github.ajalt.mordant.terminal.Terminal
import data.GitHubImpl
import data.SharedManifestData
import data.VersionUpdateState
import input.PromptType
import input.Prompts
import ktor.Ktor
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import schemas.InstallerSchema
import schemas.SchemasImpl
import kotlin.random.Random
import kotlin.system.exitProcess

object PackageVersion : KoinComponent {
    private val githubImpl: GitHubImpl by inject()

    fun Terminal.packageVersionPrompt(packageVersion: String? = null) {
        val sharedManifestData: SharedManifestData by inject()
        if (packageVersion != null) {
            isPackageVersionValid(packageVersion).also {
                if (it == null) {
                    sharedManifestData.packageVersion = packageVersion
                    setUpgradeState(sharedManifestData)
                } else {
                    println(brightRed(it))
                    exitProcess(0)
                }
            }
        } else {
            do {
                println(brightGreen(packageVersionInfo))
                println(cyan(packageVersionExample))
                val input = prompt(brightWhite(PromptType.PackageVersion.toString()))?.trim()
                val error = isPackageVersionValid(input)?.also { println(brightRed(it)) }
                if (error == null && input != null) {
                    sharedManifestData.packageVersion = input
                    setUpgradeState(sharedManifestData)
                }
                println()
            } while (error != null)
        }
    }

    private fun setUpgradeState(sharedManifestData: SharedManifestData) {
        if (sharedManifestData.updateState != VersionUpdateState.NewPackage) {
            githubImpl.getMicrosoftWingetPkgs()
                ?.getDirectoryContent(Ktor.getDirectoryPath(sharedManifestData.packageIdentifier))
                ?.map { it.name }
                ?.contains(sharedManifestData.packageVersion)
                ?.let {
                    if (it) {
                        sharedManifestData.updateState = VersionUpdateState.UpdateVersion
                    } else {
                        val versionsToCompare = listOf(
                            sharedManifestData.packageVersion,
                            sharedManifestData.latestVersion
                        )
                        when (sharedManifestData.packageVersion) {
                            getHighestVersion(versionsToCompare.filterNotNull()) -> {
                                sharedManifestData.updateState = VersionUpdateState.NewVersion
                            }
                            else -> sharedManifestData.updateState = VersionUpdateState.AddVersion
                        }
                    }
                }
        }
    }

    private fun isPackageVersionValid(
        version: String?,
        installerSchema: InstallerSchema = get<SchemasImpl>().installerSchema
    ): String? {
        val packageVersionSchema = installerSchema.definitions.packageVersion
        return when {
            version.isNullOrBlank() -> Errors.blankInput(PromptType.PackageVersion)
            version.length > packageVersionSchema.maxLength -> {
                Errors.invalidLength(max = packageVersionSchema.maxLength)
            }
            !version.matches(Regex(packageVersionSchema.pattern)) -> {
                Errors.invalidRegex(Regex(packageVersionSchema.pattern))
            }
            else -> null
        }
    }

    fun getHighestVersion(versions: List<String>): String {
        val toNatural: (String) -> String = {
            Regex("\\d+").replace(it) { matchResult ->
                matchResult.value.padStart(20)
            }
        }
        data class VersionPart(val value: Int, val supplement: String, val original: String)

        fun parseVersionPart(part: String): VersionPart {
            val value = part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
            val supplement = part.dropWhile { it.isDigit() }
            return VersionPart(value, supplement, part)
        }

        fun compareVersionParts(left: VersionPart, right: VersionPart): Int {
            return when {
                left.value != right.value -> left.value.compareTo(right.value)
                left.supplement.isEmpty() && right.supplement.isEmpty() -> 0
                left.supplement.isEmpty() -> 1
                right.supplement.isEmpty() -> -1
                else -> left.supplement.compareTo(right.supplement)
            }
        }

        fun compareVersions(left: List<VersionPart>, right: List<VersionPart>): Int {
            return left.zip(right).map { compareVersionParts(it.first, it.second) }.firstOrNull { it != 0 } ?: 0
        }

        return versions.asSequence()
            .sortedWith(compareBy(toNatural))
            .map { version ->
                version.split(".").map { versionPart ->
                    parseVersionPart(versionPart)
                }
            }.sortedWith { left, right ->
                compareVersions(left, right)
            }.last().joinToString(".") { it.original }
    }

    private fun generateRandomVersion(): String {
        val major = Random.nextInt(1, 10)
        val minor = Random.nextInt(0, 100)
        val patch = Random.nextInt(0, 10)
        return "$major.$minor.$patch"
    }

    private const val packageVersionInfo = "${Prompts.required} Enter the version."
    private val packageVersionExample = "Example: ${generateRandomVersion()}"
}
